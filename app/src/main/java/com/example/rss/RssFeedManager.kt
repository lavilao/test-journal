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
    val category: String = "Technology"
)

data class RssFeedSource(
    val name: String,
    val url: String,
    val category: String = "Technology"
)

class RssFeedManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val _sources = MutableStateFlow(
        listOf(
            RssFeedSource("The Verge", "https://www.theverge.com/rss/index.xml", "Tech"),
            RssFeedSource("Wired", "https://www.wired.com/feed/rss", "Science"),
            RssFeedSource("TechCrunch", "https://techcrunch.com/feed/", "Startups"),
            RssFeedSource("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "Gadgets")
        )
    )
    val sources: StateFlow<List<RssFeedSource>> = _sources.asStateFlow()

    private val _articles = MutableStateFlow<List<RssArticle>>(getInitialCuratedArticles())
    val articles: StateFlow<List<RssArticle>> = _articles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val imgPattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

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

    suspend fun addCustomFeed(url: String, name: String, category: String = "Custom"): Boolean = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        val safeName = name.ifBlank { "Custom RSS" }

        val newSource = RssFeedSource(safeName, trimmedUrl, category)
        val updated = _sources.value.toMutableList().apply { add(0, newSource) }
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

    private fun parseRssXml(xmlContent: String, sourceName: String, category: String): List<RssArticle> {
        val list = mutableListOf<RssArticle>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var eventType = parser.eventType
            var inItem = false

            var currentTitle = ""
            var currentLink = ""
            var currentDescription = ""
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
                                list.add(
                                    RssArticle(
                                        id = "${sourceName}_${list.size}_${System.currentTimeMillis()}",
                                        title = currentTitle.trim(),
                                        link = currentLink.trim(),
                                        description = currentDescription.take(200),
                                        pubDate = currentPubDate.take(24).ifBlank { "Just now" },
                                        sourceTitle = sourceName,
                                        imageUrl = currentImage,
                                        category = category
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

        return list.take(12)
    }

    private fun getInitialCuratedArticles(): List<RssArticle> {
        return listOf(
            RssArticle(
                id = "init_1",
                title = "The Next Generation of On-Device Personal Knowledge Systems",
                link = "https://www.theverge.com",
                description = "How modern offline databases, vector graphs, and local telemetry are replacing clunky cloud silos.",
                pubDate = "2h ago",
                sourceTitle = "The Verge",
                imageUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=800&q=80",
                category = "Technology"
            ),
            RssArticle(
                id = "init_2",
                title = "Curated Minimalist Spaces: Balancing Design & Daily Focus",
                link = "https://www.wired.com",
                description = "Exploring Scandinavian ergonomics, tactile wooden accessories, and functional home sanctuaries.",
                pubDate = "4h ago",
                sourceTitle = "Wired",
                imageUrl = "https://images.unsplash.com/photo-1513519245088-0e12902e5a38?auto=format&fit=crop&w=800&q=80",
                category = "Design"
            ),
            RssArticle(
                id = "init_3",
                title = "Morning Habits for Longevity and Mental Clarity",
                link = "https://www.bbc.com/news",
                description = "Health researchers break down why steady walking, natural light, and digital detox before noon double productivity.",
                pubDate = "5h ago",
                sourceTitle = "BBC News",
                imageUrl = "https://images.unsplash.com/photo-1506126613408-eca07ce68773?auto=format&fit=crop&w=800&q=80",
                category = "Health"
            ),
            RssArticle(
                id = "init_4",
                title = "Open Source Local AI Models Reach Parity with Web Assistants",
                link = "https://arstechnica.com",
                description = "Breakthroughs in quantized neural models allow smartphones to perform semantic recall and transcription with zero cloud roundtrips.",
                pubDate = "7h ago",
                sourceTitle = "Ars Technica",
                imageUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=800&q=80",
                category = "Startups"
            )
        )
    }
}
