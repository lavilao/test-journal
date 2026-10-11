package com.example.rss

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class RssArticle(
    val id: String,
    val title: String,
    val link: String,
    val description: String,
    val pubDate: String,
    val sourceTitle: String,
    val imageUrl: String? = null,
    val category: String = "Noticias",
    val fullContent: String = ""
)

data class RssFeedSource(
    val name: String,
    val url: String,
    val category: String = "Noticias"
)

class RssFeedManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("rss_feeds_prefs", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_SOURCES_V2 = "rss_sources_v2" // legacy StringSet, migration only
        const val KEY_SOURCES_V3 = "rss_sources_v3" // JSON array: survives any character
        const val ARTICLES_CACHE_FILE = "rss_articles_cache.json"
        const val MAX_CACHED_ARTICLES = 120
    }

    private val defaultSources = listOf(
        RssFeedSource("BBC News", "https://feeds.bbci.co.uk/news/rss.xml", "General"),
        RssFeedSource("Wired", "https://www.wired.com/feed/rss", "Tecnología"),
        RssFeedSource("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "Tecnología"),
        RssFeedSource("El País", "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", "Noticias"),
        RssFeedSource("TechCrunch", "https://techcrunch.com/feed/", "Startups")
    )

    private val _sources = MutableStateFlow<List<RssFeedSource>>(loadSavedSources())
    val sources: StateFlow<List<RssFeedSource>> = _sources.asStateFlow()

    // v1.6: the last successful fetch is cached on disk, so reopening the
    // app shows YOUR feeds' news immediately instead of resetting to a
    // default list. The curated fake articles are gone for good.
    private val _articles = MutableStateFlow<List<RssArticle>>(loadCachedArticles())
    val articles: StateFlow<List<RssArticle>> = _articles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun refreshFeeds() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        try {
            // Feeds are fetched in parallel (bounded by OkHttp's own pool):
            // 15 sources no longer take 15 × timeouts sequentially.
            val fetched = kotlinx.coroutines.coroutineScope {
                _sources.value.map { source ->
                    kotlinx.coroutines.async(Dispatchers.IO) {
                        fetchSource(source)
                    }
                }.map { it.await() }.flatten()
            }
            if (fetched.isNotEmpty()) {
                val merged = (fetched + _articles.value)
                    .distinctBy { it.title }
                    .sortedByDescending { parsePubDateMillis(it.pubDate) ?: it.id.hashCode().toLong() }
                _articles.value = merged.take(MAX_CACHED_ARTICLES)
                persistArticlesCache(_articles.value)
            }
        } finally {
            _isLoading.value = false
        }
    }

    private fun fetchSource(source: RssFeedSource): List<RssArticle> = try {
        val request = Request.Builder()
            .url(source.url)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:100.0)")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val bodyString = response.body?.string()
                if (!bodyString.isNullOrBlank()) {
                    parseRssXml(bodyString, source.name, source.category)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun fetchFullArticleText(article: RssArticle): String = withContext(Dispatchers.IO) {
        if (article.fullContent.length > 300) {
            return@withContext article.fullContent
        }
        try {
            val request = Request.Builder()
                .url(article.link)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13)")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val paragraphs = Regex("<p[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(html)
                    .map { it.groupValues[1].replace(Regex("<[^>]*>"), "").trim() }
                    .filter { it.length > 40 && !it.contains("cookie", ignoreCase = true) }
                    .take(15)
                    .joinToString("\n\n")

                if (paragraphs.isNotBlank()) {
                    return@withContext paragraphs
                }
            }
        } catch (_: Exception) {}

        article.description.ifBlank { article.title }
    }

    suspend fun addCustomFeed(url: String, name: String, category: String = "Personal"): Boolean = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val safeName = name.ifBlank { "Canal RSS" }

        val newSource = RssFeedSource(safeName, trimmedUrl, category)
        val updated = (_sources.value.filter { it.url != trimmedUrl } + newSource)
        writeSourceSet(updated)
        _sources.value = updated

        try {
            val request = Request.Builder().url(trimmedUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val xml = response.body?.string() ?: return@withContext false
                    val parsed = parseRssXml(xml, safeName, category)
                    if (parsed.isNotEmpty()) {
                        val combined = (parsed + _articles.value).distinctBy { it.title }
                        _articles.value = combined
                        return@withContext true
                    }
                }
            }
        } catch (_: Exception) {}

        true
    }

    /** Removes a feed (any feed — custom or one of the originals). */
    suspend fun removeFeed(url: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        val remaining = _sources.value.filterNot { it.url == trimmed }
        writeSourceSet(remaining)
        _sources.value = remaining
        // Drop the removed feed's articles from the current list too
        // (articles carry the source NAME, not its URL).
        val keptNames = remaining.map { it.name }.toSet()
        _articles.value = _articles.value.filter { it.sourceTitle in keptNames }
        persistArticlesCache(_articles.value)
        true
    }

    // ------------------------------------------------------------------
    // OPML import / export
    // ------------------------------------------------------------------

    /** Serializes the current feeds as OPML 2.0 (RSS & Atom outlines). */
    fun exportOpml(): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head><title>Mnemosyne feeds</title><dateCreated>")
            .append(java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                .format(java.util.Date()))
            .append("</dateCreated></head>\n")
        sb.append("  <body>\n")
        _sources.value.forEach { s ->
            sb.append("    <outline type=\"rss\" text=\"")
                .append(xmlEscape(s.name))
                .append("\" title=\"")
                .append(xmlEscape(s.name))
                .append("\" xmlUrl=\"")
                .append(xmlEscape(s.url))
                .append("\" category=\"")
                .append(xmlEscape(s.category))
                .append("\"/>\n")
        }
        sb.append("  </body>\n</opml>\n")
        return sb.toString()
    }

    /**
     * Imports feeds from OPML text. Accepts nested outlines (folders) and
     * ignores outlines without an xmlUrl. Returns how many feeds were added.
     */
    suspend fun importOpml(opmlText: String): Int = withContext(Dispatchers.IO) {
        val found = mutableListOf<RssFeedSource>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(opmlText))
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name?.lowercase() == "outline") {
                    val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                        ?: parser.getAttributeValue(null, "xmlurl")
                        ?: parser.getAttributeValue(null, "xmlURL")
                    if (!xmlUrl.isNullOrBlank() && xmlUrl.startsWith("http")) {
                        val title = parser.getAttributeValue(null, "title")
                            ?: parser.getAttributeValue(null, "text")
                            ?: xmlUrl.substringAfter("//").takeBefore('/')
                        val category = parser.getAttributeValue(null, "category")?.takeIf { it.isNotBlank() } ?: "Importado"
                        found.add(RssFeedSource(title.trim(), xmlUrl.trim(), category))
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }

        val current = _sources.value
        val toAdd = found.distinctBy { it.url }.filter { f -> current.none { it.url == f.url } }
        if (toAdd.isNotEmpty()) {
            writeSourceSet(current + toAdd)
            _sources.value = current + toAdd
        }
        toAdd.size
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun String.takeBefore(c: Char): String =
        if (contains(c)) substringBefore(c) else this

    // ------------------------------------------------------------------
    // Source persistence (v3: one JSON array string; commit() so the write
    // is on disk before the app can be killed. v2's "|"-delimited entries
    // broke whenever a name/category contained a pipe.)
    // ------------------------------------------------------------------

    private fun loadSavedSources(): List<RssFeedSource> {
        if (prefs.contains(KEY_SOURCES_V3)) {
            // The key exists: an EMPTY list is a deliberate user choice —
            // never fall back to defaults over it.
            return try {
                parseSourcesJson(prefs.getString(KEY_SOURCES_V3, "[]") ?: "[]")
            } catch (_: Exception) {
                defaultSources
            }
        }

        // --- Migration from v2 (or first run) ---
        val migrated: List<RssFeedSource> = if (prefs.contains(KEY_SOURCES_V2)) {
            prefs.getStringSet(KEY_SOURCES_V2, emptySet())
                ?.mapNotNull { decodeLegacyEntry(it) }
                .orEmpty()
                .ifEmpty { defaultSources }
        } else {
            val oldCustom = prefs.getStringSet("custom_rss_sources", null)
                ?.mapNotNull { decodeLegacyEntry(it) }
                .orEmpty()
            (oldCustom + defaultSources).distinctBy { it.url }
        }
        writeSourceSet(migrated)
        return migrated
    }

    private fun parseSourcesJson(json: String): List<RssFeedSource> {
        val arr = JSONArray(json)
        val out = mutableListOf<RssFeedSource>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            val url = o.optString("url").trim()
            if (url.startsWith("http")) {
                out.add(RssFeedSource(name.ifBlank { url.substringAfter("//").takeBefore('/') }, url, o.optString("category").ifBlank { "Noticias" }))
            }
        }
        return out
    }

    private fun writeSourceSet(sources: List<RssFeedSource>) {
        val arr = JSONArray()
        sources.forEach { s ->
            arr.put(JSONObject().put("name", s.name).put("url", s.url).put("category", s.category))
        }
        prefs.edit()
            .putString(KEY_SOURCES_V3, arr.toString())
            // commit(): synchronous — the list survives even if the process
            // dies the instant the user closes the app.
            .commit()
    }

    /** Tolerates the old v2 "|" and pre-v2 "|||" entries. */
    private fun decodeLegacyEntry(entry: String): RssFeedSource? {
        val parts = if (entry.contains("|||")) entry.split("|||") else entry.split("|")
        return if (parts.size >= 2 && parts[1].startsWith("http")) {
            RssFeedSource(parts[0], parts[1], parts.getOrElse(2) { "Noticias" })
        } else null
    }

    private fun parseRssXml(xmlContent: String, sourceName: String, category: String): List<RssArticle> {
        val list = mutableListOf<RssArticle>()
        val imgPattern = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>", Pattern.CASE_INSENSITIVE)

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var inItem = false

            var currentTitle = ""
            var currentLink = ""
            var currentDescription = ""
            var currentContent = ""
            var currentPubDate = ""
            var currentImage: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (tag == "item" || tag == "entry") {
                            inItem = true
                            currentTitle = ""
                            currentLink = ""
                            currentDescription = ""
                            currentContent = ""
                            currentPubDate = ""
                            currentImage = null
                        } else if (inItem) {
                            when (tag) {
                                "title" -> currentTitle = parser.nextText()
                                "link" -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    currentLink = if (!href.isNullOrBlank()) href else parser.nextText()
                                }
                                "description", "summary" -> {
                                    val text = parser.nextText()
                                    currentDescription = text.replace(Regex("<[^>]*>"), "").trim()
                                    if (currentImage == null) {
                                        val matcher = imgPattern.matcher(text)
                                        if (matcher.find()) {
                                            currentImage = matcher.group(1)
                                        }
                                    }
                                }
                                "content:encoded", "content" -> {
                                    val text = parser.nextText()
                                    currentContent = text.replace(Regex("<[^>]*>"), "").trim()
                                }
                                "pubdate", "published", "updated" -> currentPubDate = parser.nextText()
                                "enclosure" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    val type = parser.getAttributeValue(null, "type")
                                    if (!url.isNullOrBlank() && (type?.startsWith("image") == true || url.contains(".jpg") || url.contains(".png") || url.contains(".webp"))) {
                                        currentImage = url
                                    }
                                }
                                "media:content", "media:thumbnail" -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (!url.isNullOrBlank()) {
                                        currentImage = url
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tag == "item" || tag == "entry") {
                            if (currentTitle.isNotBlank()) {
                                val fullArticle = if (currentContent.isNotBlank()) currentContent else currentDescription
                                list.add(
                                    RssArticle(
                                        id = "${sourceName}_${list.size}_${System.currentTimeMillis()}",
                                        title = currentTitle.trim(),
                                        link = currentLink.trim(),
                                        description = currentDescription,
                                        pubDate = currentPubDate.take(24).ifBlank { "Reciente" },
                                        sourceTitle = sourceName,
                                        imageUrl = currentImage,
                                        category = category,
                                        fullContent = fullArticle
                                    )
                                )
                            }
                            inItem = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}

        return list.take(15)
    }

    // ------------------------------------------------------------------
    // Article disk cache (v1.6): the news list survives app restarts. No
    // more resetting to fabricated "curated" articles — if there is no
    // cache and no network, the list is honestly empty until a refresh.
    // ------------------------------------------------------------------

    private fun cacheFile(): File = File(context.filesDir, ARTICLES_CACHE_FILE)

    private fun loadCachedArticles(): List<RssArticle> = try {
        val f = cacheFile()
        if (f.exists()) parseArticlesJson(f.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun persistArticlesCache(articles: List<RssArticle>) {
        try {
            val f = cacheFile()
            val tmp = File(f.parentFile, "$ARTICLES_CACHE_FILE.tmp")
            tmp.writeText(buildArticlesJson(articles.take(MAX_CACHED_ARTICLES)))
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun buildArticlesJson(articles: List<RssArticle>): String {
        val arr = JSONArray()
        articles.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("title", a.title)
                    .put("link", a.link)
                    .put("description", a.description)
                    .put("pubDate", a.pubDate)
                    .put("sourceTitle", a.sourceTitle)
                    .put("imageUrl", a.imageUrl ?: "")
                    .put("category", a.category)
                    .put("fullContent", a.fullContent)
            )
        }
        return arr.toString()
    }

    private fun parseArticlesJson(json: String): List<RssArticle> {
        val arr = JSONArray(json)
        val out = mutableListOf<RssArticle>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title")
            if (title.isBlank()) continue
            out.add(
                RssArticle(
                    id = o.optString("id", "cache_$i"),
                    title = title,
                    link = o.optString("link"),
                    description = o.optString("description"),
                    pubDate = o.optString("pubDate", "Reciente"),
                    sourceTitle = o.optString("sourceTitle"),
                    imageUrl = o.optString("imageUrl").takeIf { it.isNotBlank() },
                    category = o.optString("category", "Noticias"),
                    fullContent = o.optString("fullContent")
                )
            )
        }
        return out
    }

    /** Best-effort pubDate parse (RFC-822 or ISO-8601) for newest-first order. */
    private fun parsePubDateMillis(raw: String): Long? {
        val s = raw.trim()
        if (s.isBlank() || s.equals("Reciente", ignoreCase = true)) return null
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z", "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss z", "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd"
        )
        for (p in patterns) {
            try {
                val fmt = java.text.SimpleDateFormat(p, Locale.US)
                fmt.isLenient = true
                return fmt.parse(s)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
