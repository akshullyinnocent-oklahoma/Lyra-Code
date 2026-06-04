package com.yukisoffd.lyracode.ai

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String,
)

data class WebPageResult(
    val title: String,
    val url: String,
    val text: String,
)

class WebViewWebAgent(context: Context) {
    private val context = context
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 6): String {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotBlank()) { "搜索关键词不能为空" }
        val maxResults = limit.coerceIn(1, 10)
        val engines = listOf(
            SearchEngine("Google", "https://www.google.com/search?hl=zh-CN&q=${Uri.encode(cleanQuery)}"),
            SearchEngine("Bing", "https://www.bing.com/search?q=${Uri.encode(cleanQuery)}"),
            SearchEngine("必应", "https://cn.bing.com/search?q=${Uri.encode(cleanQuery)}"),
            SearchEngine("百度", "https://www.baidu.com/s?wd=${Uri.encode(cleanQuery)}"),
        )
        var lastError = ""
        val results = (withTimeoutOrNull(18_000L) { httpSearch(engines, cleanQuery, maxResults) } ?: emptyList())
            .takeIf { it.isNotEmpty() }
            ?: engines.firstNotNullOfOrNull { engine ->
                runCatching {
                    val json = loadAndEvaluate(url = engine.url, script = searchScript(maxResults), timeoutMs = 4_000L)
                    parseSearchResults(json).takeIf { it.isNotEmpty() }
                }.onFailure {
                    lastError = "${engine.name}: ${it::class.java.simpleName}: ${it.message.orEmpty()}"
                    Log.w(TAG, "WebView search failed: ${engine.url}", it)
                }.getOrNull()
            }.orEmpty()
        if (results.isEmpty()) return "未找到可用搜索结果。lastError=$lastError"
        return results.joinToString("\n\n") { result ->
            "title: ${result.title}\nurl: ${result.url}\nsnippet: ${result.snippet.take(500)}"
        }
    }

    private suspend fun httpSearch(engines: List<SearchEngine>, query: String, limit: Int): List<WebSearchResult> = withContext(Dispatchers.IO) {
        engines.firstNotNullOfOrNull { engine ->
            runCatching {
                val results = when (engine.name) {
                    "Bing" -> parseBingRss(httpGet("https://www.bing.com/search?q=${Uri.encode(query)}&format=rss"), limit)
                        .ifEmpty { parseLinksFromHtml(httpGet(engine.url), limit) }
                    "必应" -> parseBingRss(httpGet("https://cn.bing.com/search?q=${Uri.encode(query)}&format=rss"), limit)
                        .ifEmpty { parseLinksFromHtml(httpGet(engine.url), limit) }
                    else -> parseLinksFromHtml(httpGet(engine.url), limit)
                }
                results.takeIf { it.isNotEmpty() }
            }.onFailure {
                Log.w(TAG, "HTTP search failed: ${engine.name}", it)
            }.getOrNull()
        }.orEmpty()
    }

    suspend fun readPage(url: String): String {
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) { "只支持 http/https 网页 URL" }
        val page = runCatching {
            val json = loadAndEvaluate(url = cleanUrl, script = pageScript(), timeoutMs = 10_000L)
            parsePage(json)
        }.onFailure {
            Log.w(TAG, "WebView read page failed: $cleanUrl", it)
        }.getOrElse {
            withTimeoutOrNull(8_000L) { httpReadFallback(cleanUrl) }
                ?: WebPageResult("", cleanUrl, "网页读取超时。")
        }
        return "title: ${page.title}\nurl: ${page.url}\n\n${page.text.ifBlank { "页面没有可读取文本。" }}"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadAndEvaluate(url: String, script: String, timeoutMs: Long): String = withContext(Dispatchers.Main) {
        withTimeout(timeoutMs) {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.loadsImagesAutomatically = false
            webView.settings.blockNetworkImage = true
            try {
                suspendCancellableCoroutine { continuation ->
                    var evaluated = false
                    fun evaluate(view: WebView) {
                        if (evaluated || !continuation.isActive) return
                        evaluated = true
                        view.postDelayed({
                            view.evaluateJavascript(script) { value ->
                                Log.d(TAG, "Loaded $url, resultChars=${value?.length ?: 0}")
                                if (continuation.isActive) continuation.resume(value.orEmpty())
                            }
                        }, 400L)
                    }
                    continuation.invokeOnCancellation { webView.destroy() }
                    webView.webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            if (newProgress >= 100) evaluate(view)
                        }
                    }
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

                        override fun onPageFinished(view: WebView, loadedUrl: String) {
                            evaluate(view)
                        }
                    }
                    Log.d(TAG, "Loading $url")
                    webView.loadUrl(url)
                }.also {
                    webView.destroy()
                }
            } catch (error: Throwable) {
                webView.destroy()
                throw error
            }
        }
    }

    private fun searchScript(limit: Int): String = """
        (function() {
            const anchors = Array.from(document.querySelectorAll('a'));
            const rows = [];
            const seen = new Set();
            for (const a of anchors) {
                let href = a.href || '';
                let title = (a.innerText || a.textContent || '').replace(/\s+/g, ' ').trim();
                if (!href || !title || title.length < 2) continue;
                try {
                    const parsed = new URL(href);
                    const uddg = parsed.searchParams.get('uddg');
                    if (uddg) href = decodeURIComponent(uddg);
                    const googleTarget = parsed.searchParams.get('q');
                    if (parsed.hostname.includes('google.') && googleTarget && /^https?:\/\//i.test(googleTarget)) href = googleTarget;
                    const baiduTarget = parsed.searchParams.get('url');
                    if (parsed.hostname.includes('baidu.') && baiduTarget && /^https?:\/\//i.test(baiduTarget)) href = baiduTarget;
                } catch (error) {}
                if (!/^https?:\/\//i.test(href)) continue;
                if (isSearchEngineUrl(href)) continue;
                if (seen.has(href)) continue;
                seen.add(href);
                let parent = a.closest('div, article, li') || a.parentElement;
                let snippet = parent ? (parent.innerText || '').replace(/\s+/g, ' ').trim() : '';
                if (snippet.startsWith(title)) snippet = snippet.slice(title.length).trim();
                rows.push({ title, url: href, snippet });
                if (rows.length >= $limit) break;
            }
            function isSearchEngineUrl(url) {
                try {
                    const parsed = new URL(url);
                    const host = parsed.hostname;
                    const path = parsed.pathname;
                    return (host.includes('google.') && path.startsWith('/search')) ||
                        (host.includes('bing.com') && path.startsWith('/search')) ||
                        (host.includes('baidu.com') && (path === '/s' || path.startsWith('/s?')));
                } catch (error) {
                    return false;
                }
            }
            return rows;
        })()
    """.trimIndent()

    private fun pageScript(): String = """
        (function() {
            const blocked = ['script', 'style', 'noscript', 'svg', 'canvas', 'iframe', 'nav', 'footer'];
            const clone = document.body ? document.body.cloneNode(true) : document.documentElement.cloneNode(true);
            for (const tag of blocked) {
                clone.querySelectorAll(tag).forEach(function(node) { node.remove(); });
            }
            const text = (clone.innerText || clone.textContent || '').replace(/\n{3,}/g, '\n\n').replace(/[ \t]{2,}/g, ' ').trim();
            return {
                title: document.title || '',
                url: location.href,
                text: text.slice(0, 20000)
            };
        })()
    """.trimIndent()

    private suspend fun httpReadFallback(url: String): WebPageResult = withContext(Dispatchers.IO) {
        val html = httpGet(url)
        val title = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.htmlToText()
            .orEmpty()
        WebPageResult(title, url, htmlToPlainText(html).take(20_000))
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
            .build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    private fun parseLinksFromHtml(html: String, limit: Int): List<WebSearchResult> {
        val seen = mutableSetOf<String>()
        val anchorRegex = Regex("""<a\b[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return anchorRegex.findAll(html).mapNotNull { match ->
            var url = match.groupValues[1].htmlToText()
            val title = match.groupValues[2].htmlToText()
            if (url.startsWith("/l/?")) {
                Uri.parse("https://duckduckgo.com$url").getQueryParameter("uddg")?.let { url = it }
            }
            Uri.parse(url).getQueryParameter("q")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { url = it }
            Uri.parse(url).getQueryParameter("url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { url = it }
            if (url.startsWith("/url?")) {
                Uri.parse("https://www.google.com$url").getQueryParameter("q")?.let { url = it }
            }
            if (url.startsWith("/link?")) {
                Uri.parse("https://www.baidu.com$url").getQueryParameter("url")?.let { url = it }
            }
            if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
            if (isSearchEngineUrl(url)) return@mapNotNull null
            if (title.length < 2 || !seen.add(url)) return@mapNotNull null
            WebSearchResult(title = title.take(200), url = url, snippet = "")
        }.take(limit).toList()
    }

    private fun parseBingRss(xml: String, limit: Int): List<WebSearchResult> {
        val itemRegex = Regex("""<item>(.*?)</item>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        return itemRegex.findAll(xml).mapNotNull { match ->
            val item = match.groupValues[1]
            val title = item.xmlTag("title").htmlToText()
            val link = item.xmlTag("link").htmlToText()
            val snippet = item.xmlTag("description").htmlToText()
            if (title.isBlank() || !link.startsWith("http")) return@mapNotNull null
            WebSearchResult(title = title.take(200), url = link, snippet = snippet.take(500))
        }.take(limit).toList()
    }

    private fun isSearchEngineUrl(url: String): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = parsed.host.orEmpty()
        val path = parsed.path.orEmpty()
        return (host.contains("google.") && path.startsWith("/search")) ||
            (host.contains("bing.com") && path.startsWith("/search")) ||
            (host.contains("baidu.com") && path == "/s")
    }

    private fun parseSearchResults(raw: String): List<WebSearchResult> {
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    WebSearchResult(
                        title = item.optString("title"),
                        url = item.optString("url"),
                        snippet = item.optString("snippet"),
                    ),
                )
            }
        }
    }

    private fun parsePage(raw: String): WebPageResult {
        val root = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        return WebPageResult(
            title = root.optString("title"),
            url = root.optString("url"),
            text = root.optString("text"),
        )
    }

    private fun htmlToPlainText(html: String): String {
        return html
            .replace(Regex("""<script\b[^>]*>.*?</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<style\b[^>]*>.*?</style>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("""<br\s*/?>|</p>|</div>|</li>|</h[1-6]>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<[^>]+>"""), " ")
            .htmlToText()
            .replace(Regex("[ \t]{2,}"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun String.htmlToText(): String {
        return replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun String.xmlTag(name: String): String {
        return Regex("""<$name[^>]*>(.*?)</$name>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
            .removePrefix("<![CDATA[")
            .removeSuffix("]]>")
    }

    companion object {
        private const val TAG = "LyraWebAgent"
    }
}

private data class SearchEngine(
    val name: String,
    val url: String,
)
