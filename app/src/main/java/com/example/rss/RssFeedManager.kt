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
import org.xmlpull.v1.XmlPullParser
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
        const val KEY_SOURCES_V2 = "rss_sources_v2"
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

    private val _articles = MutableStateFlow<List<RssArticle>>(getInitialCuratedArticles())
    val articles: StateFlow<List<RssArticle>> = _articles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun refreshFeeds() = withContext(Dispatchers.IO) {
        _isLoading.value = true
        val fetchedList = mutableListOf<RssArticle>()

        for (source in _sources.value) {
            try {
                val request = Request.Builder()
                    .url(source.url)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:100.0)")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrBlank()) {
                            val parsed = parseRssXml(bodyString, source.name, source.category)
                            fetchedList.addAll(parsed)
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore individual feed failures; fallback continues
            }
        }

        if (fetchedList.isNotEmpty()) {
            _articles.value = fetchedList.shuffled().distinctBy { it.title }
        }
        _isLoading.value = false
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
        persistSource(newSource)
        val updated = (_sources.value.filter { it.url != trimmedUrl } + newSource)
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
        val remaining = readSourceSet().mapNotNull { decodeEntry(it) }
            .filterNot { it.url == trimmed }
        writeSourceSet(remaining)
        _sources.value = remaining
        // Drop the removed feed's articles from the current list too
        // (articles carry the source NAME, not its URL).
        _articles.value = _articles.value.filterNot { article ->
            remaining.none { it.name == article.sourceTitle }
        }
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
    // Source persistence (v2: one StringSet, defaults deletable)
    // ------------------------------------------------------------------

    private fun loadSavedSources(): List<RssFeedSource> {
        // Migration: the old format only stored CUSTOM feeds and re-appended
        // the 5 defaults forever (they could not be deleted). v2 stores the
        // complete list, so removals stick.
        if (!prefs.contains(KEY_SOURCES_V2)) {
            val oldCustom = prefs.getStringSet("custom_rss_sources", null)
                ?.mapNotNull { entry ->
                    val parts = entry.split("|||")
                    if (parts.size >= 2) RssFeedSource(parts[0], parts[1], parts.getOrElse(2) { "Noticias" }) else null
                }.orEmpty()
            val merged = (oldCustom + defaultSources).distinctBy { it.url }
            writeSourceSet(merged)
            return merged
        }
        return readSourceSet().mapNotNull { decodeEntry(it) }
    }

    private fun readSourceSet(): Set<String> =
        prefs.getStringSet(KEY_SOURCES_V2, emptySet())?.toSet() ?: emptySet()

    private fun writeSourceSet(sources: List<RssFeedSource>) {
        prefs.edit()
            .putStringSet(KEY_SOURCES_V2, sources.map(::encodeEntry).toSet())
            .apply()
    }

    private fun persistSource(source: RssFeedSource) {
        val current = readSourceSet().mapNotNull { decodeEntry(it) }
        writeSourceSet((current.filterNot { it.url == source.url } + source))
    }

    private fun encodeEntry(s: RssFeedSource) = "${s.name}|${s.url}|${s.category}"

    private fun decodeEntry(entry: String): RssFeedSource? {
        // Tolerates both the v2 "|" format and legacy "|||" entries.
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

    private fun getInitialCuratedArticles(): List<RssArticle> {
        return listOf(
            RssArticle(
                id = "init_1",
                title = "La revolución del procesamiento en dispositivo: Privacidad y eficiencia",
                link = "https://www.theverge.com",
                description = "Cómo las bases de datos locales, el procesamiento offline y el cifrado en el dispositivo están transformando las aplicaciones móviles modernas.",
                pubDate = "Hace 2h",
                sourceTitle = "Tecnología",
                imageUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80",
                category = "Tecnología",
                fullContent = "Cómo las bases de datos locales, el procesamiento offline y el cifrado en el dispositivo están transformando las aplicaciones móviles modernas.\n\nLos usuarios hoy en día demandan interfaces instantáneas y soberanía de datos sin depender de servidores remotos para cada interacción básica."
            ),
            RssArticle(
                id = "init_2",
                title = "Diseño minimalista y enfoque diario en el espacio de trabajo",
                link = "https://www.wired.com",
                description = "La ergonomía, los materiales cálidos y la reducción del ruido visual mejoran la concentración en las tareas cotidianas.",
                pubDate = "Hace 4h",
                sourceTitle = "Wired",
                imageUrl = "https://images.unsplash.com/photo-1513519245088-0e12902e5a38?auto=format&fit=crop&w=800&q=80",
                category = "Diseño",
                fullContent = "La ergonomía, los materiales cálidos y la reducción del ruido visual mejoran la concentración en las tareas cotidianas.\n\nDiseñar entornos sin distracciones permite mantener la calma mental y una mayor atención a los proyectos importantes."
            ),
            RssArticle(
                id = "init_3",
                title = "Hábitos matutinos para potenciar la claridad mental y el bienestar",
                link = "https://www.bbc.com/news",
                description = "Investigadores analizan el impacto de caminar a paso constante, la luz natural y planificar los objetivos antes de iniciar la jornada.",
                pubDate = "Hace 5h",
                sourceTitle = "Salud",
                imageUrl = "https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=800&q=80",
                category = "Salud",
                fullContent = "Investigadores analizan el impacto de caminar a paso constante, la luz natural y planificar los objetivos antes de iniciar la jornada.\n\nUn paseo matutino y unos minutos de escritura reflexiva bastan para estructurar el día con serenidad."
            )
        )
    }
}
