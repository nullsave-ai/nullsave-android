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
import java.io.ByteArrayInputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

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
                    Log.d("NullSaveLog", "${consoleMessage.message()} (Line ${consoleMessage.lineNumber()})")
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

                // 1. Independent Local API Engine (Zero Third-Party Calls)
                if (url.contains("/api/")) {
                    val apiPath = url.substring(url.indexOf("/api/"))
                    val jsonResponse = handleLocalApi(apiPath, request.method)
                    val stream = ByteArrayInputStream(jsonResponse.toByteArray(Charsets.UTF_8))
                    val headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "Access-Control-Allow-Origin" to "*",
                        "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS",
                        "Access-Control-Allow-Headers" to "*"
                    )
                    return WebResourceResponse("application/json", "UTF-8", 200, "OK", headers, stream)
                }

                // 2. Serve static assets locally from assets/web/
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
                    Log.w("NullSaveAsset", "Asset not found: $assetPath for URL: $url")
                    null
                }
            }
        }

        webView.loadUrl("https://nullsave.local/index.html")
    }

    private fun handleLocalApi(apiPath: String, method: String): String {
        return when {
            apiPath.contains("auth/status") -> {
                """{"ok": true, "authenticated": true, "user": {"email": "admin@nullsave.app", "plan": "PRO", "expires": "2035-12-31"}}"""
            }
            apiPath.contains("auth/login") || apiPath.contains("auth/register") -> {
                """{"ok": true, "token": "nullsave-jwt-token-local", "user": {"email": "admin@nullsave.app", "plan": "PRO"}}"""
            }
            apiPath.contains("account/status") -> {
                """{"ok": true, "status": "active", "tier": "PRO", "max_devices": 10, "devices": 1}"""
            }
            apiPath.contains("account/settings") -> {
                """{"ok": true, "theme": "dark", "notifications": true, "currency": "USD"}"""
            }
            apiPath.contains("config/tickers") -> {
                """{"ok": true, "tickers": ["BTCUSD", "ETHUSD", "XAUUSD", "EURUSD", "GBPUSD", "US500", "USTEC", "GER40", "USDCAD", "USDJPY", "XAGUSD", "XTIUSD"]}"""
            }
            apiPath.contains("config/profiles") -> {
                """{"ok": true, "profiles": [{"id": "default", "name": "Default NullSave Profile"}]}"""
            }
            apiPath.contains("chart-presets") -> {
                """{"ok": true, "presets": []}"""
            }
            apiPath.contains("chart-templates") -> {
                """{"ok": true, "templates": []}"""
            }
            apiPath.contains("regime/macro") || apiPath.contains("pulse") -> {
                """{"ok": true, "regime": "ACCUMULATION", "volatility": "NORMAL", "pulse": 0.85, "gex_sentiment": "BULLISH"}"""
            }
            apiPath.contains("signals") -> {
                """{"ok": true, "signals": []}"""
            }
            apiPath.contains("orders") -> {
                """{"ok": true, "orders": [], "pending": []}"""
            }
            apiPath.contains("positions") -> {
                """{"ok": true, "positions": []}"""
            }
            apiPath.contains("messages") -> {
                """{"ok": true, "messages": [], "unread": 0}"""
            }
            apiPath.contains("health") -> {
                """{"status": "healthy", "server": "NullSave-Independent-Core", "version": "2.45.0"}"""
            }
            else -> {
                """{"ok": true, "status": "success", "data": {}}"""
            }
        }
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
