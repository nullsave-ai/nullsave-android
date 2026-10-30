package xyz.mkterminal.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val remoteApiBase = "https://api.mkterminal.xyz"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage != null) {
                    Log.d("NullsaveWeb", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                val path = request.url.path ?: ""

                // 1. Intercept /api/ calls and proxy to Cloud Backend
                if (url.contains("/api/")) {
                    val apiPath = url.substring(url.indexOf("/api/"))
                    val targetUrl = remoteApiBase + apiPath
                    return try {
                        val reqBuilder = Request.Builder().url(targetUrl)
                        for ((key, value) in request.requestHeaders) {
                            if (!key.equals("Host", ignoreCase = true) && !key.equals("Origin", ignoreCase = true)) {
                                reqBuilder.addHeader(key, value)
                            }
                        }
                        reqBuilder.addHeader("Origin", "https://api.mkterminal.xyz")
                        reqBuilder.addHeader("Referer", "https://api.mkterminal.xyz/")

                        val method = request.method
                        if (method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true)) {
                            reqBuilder.method(method, ByteArray(0).toRequestBody(null))
                        } else {
                            reqBuilder.method(method, null)
                        }

                        val response = client.newCall(reqBuilder.build()).execute()
                        val responseBody = response.body?.byteStream() ?: ByteArrayInputStream(ByteArray(0))
                        val contentType = response.header("Content-Type", "application/json") ?: "application/json"
                        val headersMap = mutableMapOf<String, String>()
                        for (i in 0 until response.headers.size) {
                            headersMap[response.headers.name(i)] = response.headers.value(i)
                        }
                        headersMap["Access-Control-Allow-Origin"] = "*"
                        headersMap["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
                        headersMap["Access-Control-Allow-Headers"] = "*"
                        val statusMsg = if (response.message.isEmpty()) "OK" else response.message
                        WebResourceResponse(
                            contentType.split(";")[0],
                            "UTF-8",
                            response.code,
                            statusMsg,
                            headersMap,
                            responseBody
                        )
                    } catch (e: Exception) {
                        Log.e("NullsaveAPI", "Error proxying API call: $targetUrl", e)
                        null
                    }
                }

                // 2. Serve static assets directly from assets/web/
                val cleanPath = when {
                    path.isEmpty() || path == "/" -> "index.html"
                    path.startsWith("/") -> path.substring(1)
                    else -> path
                }

                val assetPath = "web/$cleanPath"
                return try {
                    val stream: InputStream = assets.open(assetPath)
                    val mime = getMimeType(cleanPath)
                    val headers = mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Cache-Control" to "no-cache"
                    )
                    WebResourceResponse(mime, "UTF-8", 200, "OK", headers, stream)
                } catch (e: Exception) {
                    Log.w("NullsaveAsset", "Asset not found: $assetPath for URL: $url")
                    null
                }
            }
        }

        webView.loadUrl("https://nullsave.local/index.html")
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") -> "text/html"
            path.endsWith(".js") -> "application/javascript"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".svg") -> "image/svg+xml"
            path.endsWith(".ico") -> "image/x-icon"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".woff") -> "font/woff"
            path.endsWith(".woff2") -> "font/woff2"
            path.endsWith(".ttf") -> "font/ttf"
            else -> "application/octet-stream"
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
