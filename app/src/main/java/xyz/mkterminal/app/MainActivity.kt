package xyz.mkterminal.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Random

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val random = Random()
    private var allBarsJson: JSONObject? = null
    private var cachedPayload: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        loadBarsData()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

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

                // 1. Intercept All Local API Calls
                if (path.startsWith("/api/") || url.contains("/api/")) {
                    val apiPath = if (path.startsWith("/api/")) path else url.substring(url.indexOf("/api/"))
                    val jsonResponse = handleLocalApi(apiPath, request.method)
                    val stream = ByteArrayInputStream(jsonResponse.toByteArray(StandardCharsets.UTF_8))
                    val headers = mapOf(
                        "Content-Type" to "application/json; charset=utf-8",
                        "Access-Control-Allow-Origin" to "*",
                        "Access-Control-Allow-Methods" to "GET, POST, PUT, DELETE, OPTIONS, PATCH",
                        "Access-Control-Allow-Headers" to "*"
                    )
                    return WebResourceResponse("application/json", "UTF-8", 200, "OK", headers, stream)
                }

                // 2. Direct Local Asset Serving from assets/web/
                if (url.startsWith("https://nullsave.local/") || url.startsWith("http://nullsave.local/")) {
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

                // 3. Fallback for other requests
                return super.shouldInterceptRequest(view, request)
            }
        }

        webView.loadUrl("https://nullsave.local/index.html")
    }

    private fun loadBarsData() {
        try {
            val stream = assets.open("data/all_bars.json")
            val text = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            allBarsJson = JSONObject(text)
        } catch (e: Exception) {
            Log.e("NullSave", "Failed to load all_bars.json", e)
        }
    }

    private fun readDataAsset(filename: String): String {
        return try {
            assets.open("data/$filename").bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e("NullSave", "Asset read error: $filename", e)
            """{"status": "ok", "ok": true}"""
        }
    }

    private fun handleLocalApi(apiPath: String, method: String): String {
        return when {
            // Auth & Account
            apiPath.contains("auth/status") -> {
                """{"status": "ok", "authenticated": true, "user_email": "nullsave@terminal.local", "subscription_status": "active", "subscription_type": "pro", "is_whitelist": true}"""
            }
            apiPath.contains("auth/login") -> {
                """{"status": "ok", "token": "nullsave-jwt-token-active", "user_email": "nullsave@terminal.local", "subscription_status": "active", "subscription_type": "pro", "is_whitelist": true, "trial_expires_at": "2036-12-31T23:59:59Z", "subscription_expires_at": "2036-12-31T23:59:59Z"}"""
            }
            apiPath.contains("auth/register") -> {
                """{"status": "ok", "message": "Account created. You can now use NullSave."}"""
            }
            apiPath.contains("auth/verify-otp") -> {
                """{"status": "ok", "message": "Verified successfully."}"""
            }
            apiPath.contains("account/status") -> {
                """{"status": "ok", "ok": true, "tier": "PRO", "max_devices": 10, "devices": 1, "expires_at": "2036-12-31T23:59:59Z"}"""
            }
            apiPath.contains("account/settings") -> {
                """{"status": "ok", "ok": true, "theme": "dark", "currency": "USD", "notifications": true}"""
            }

            // Health & System
            apiPath.contains("health") -> {
                readDataAsset("api_health.json")
            }
            apiPath.contains("consumer/status") -> {
                """{"status": "ready", "connected": true, "version": "2.45.0"}"""
            }
            apiPath.contains("sync/status") -> {
                """{"format_version_ok": true, "last_synced_at": "2026-10-30T12:00:00Z", "status": "synchronized"}"""
            }
            apiPath.contains("sync/refresh") -> {
                """{"ok": true, "status": "ok"}"""
            }

            // Core Config
            apiPath.contains("config") -> {
                readDataAsset("api_config.json")
            }

            // Real-Time Ticks
            apiPath.contains("ticks") -> {
                getLiveTicksJson()
            }

            // Payload & Analytics
            apiPath.contains("payload") -> {
                if (cachedPayload == null) {
                    cachedPayload = readDataAsset("api_payload.json")
                }
                cachedPayload!!
            }

            // State (Weekly / Regime / Quant Model States)
            apiPath.contains("state") && !apiPath.contains("fundamental") -> {
                readDataAsset("api_state.json")
            }

            // Signals
            apiPath.contains("signals") -> {
                readDataAsset("api_signals.json")
            }

            // Lifecycle
            apiPath.contains("lifecycle") -> {
                readDataAsset("api_lifecycle.json")
            }

            // Fundamental & Macro Flows
            apiPath.contains("fundamental") || apiPath.contains("macro") -> {
                readDataAsset("api_fundamental.json")
            }

            // Historical Bars
            apiPath.contains("bars") -> {
                handleBarsRequest(apiPath)
            }

            // Options Cockpit & GEX
            apiPath.contains("options") -> {
                handleOptionsRequest(apiPath)
            }

            // Cones
            apiPath.contains("cones") -> {
                """{"status": "ok", "cones": {}}"""
            }

            // MT5 & Execution
            apiPath.contains("mt5/status") -> {
                """{"status": "connected", "connected": true, "ping": 12, "broker": "NullSave Core Engine"}"""
            }
            apiPath.contains("orders") -> {
                """{"status": "ok", "ok": true, "orders": [], "pending": []}"""
            }
            apiPath.contains("positions") -> {
                """{"status": "ok", "ok": true, "positions": []}"""
            }
            apiPath.contains("messages") -> {
                """{"status": "ok", "ok": true, "messages": [], "unread": 0}"""
            }
            apiPath.contains("pulse") -> {
                """{"status": "ok", "ok": true, "pulse": 0.88, "regime": "EXPANSION", "volatility": "NORMAL"}"""
            }
            apiPath.contains("version") -> {
                """{"status": "ok", "version": "2.45.0", "latest_version": "2.45.0", "update_available": false}"""
            }

            else -> {
                """{"status": "ok", "ok": true, "data": {}}"""
            }
        }
    }

    private fun handleBarsRequest(apiPath: String): String {
        return try {
            // Path pattern: /api/bars/{ticker}?timeframe={tf}&count={count}
            val clean = apiPath.substringAfter("bars/").substringBefore("?")
            val ticker = clean.replace("/", "").trim().uppercase()
            val bars = allBarsJson?.optJSONArray(ticker) ?: allBarsJson?.optJSONArray("XAUUSD")
            if (bars != null) {
                """{"status": "ok", "bars": $bars}"""
            } else {
                """{"status": "ok", "bars": []}"""
            }
        } catch (e: Exception) {
            """{"status": "ok", "bars": []}"""
        }
    }

    private fun handleOptionsRequest(apiPath: String): String {
        return try {
            val upper = apiPath.uppercase()
            val sym = when {
                upper.contains("BTC") -> "BTCUSD"
                upper.contains("US500") -> "US500"
                upper.contains("USTEC") -> "USTEC"
                upper.contains("XAG") -> "XAGUSD"
                upper.contains("XTI") -> "XTIUSD"
                else -> "XAUUSD" // Default Gold
            }
            readDataAsset("api_options_$sym.json")
        } catch (e: Exception) {
            readDataAsset("api_options_XAUUSD.json")
        }
    }

    private fun getLiveTicksJson(): String {
        val baseTicksText = readDataAsset("api_ticks.json")
        return try {
            val json = JSONObject(baseTicksText)
            val ticksObj = json.optJSONObject("ticks")
            if (ticksObj != null) {
                val currentTime = System.currentTimeMillis() / 1000
                val keys = ticksObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val tick = ticksObj.getJSONObject(k)
                    val last = tick.getDouble("last")
                    // Tiny micro-oscillation (+/- 0.03%) for realistic live pulsation
                    val delta = (random.nextDouble() - 0.49) * (last * 0.0003)
                    val newPrice = Math.round((last + delta) * 100.0) / 100.0
                    val spread = tick.optDouble("spread", 0.50)
                    tick.put("last", newPrice)
                    tick.put("bid", Math.round((newPrice - spread / 2.0) * 100.0) / 100.0)
                    tick.put("ask", Math.round((newPrice + spread / 2.0) * 100.0) / 100.0)
                    tick.put("time", currentTime)
                }
            }
            json.toString()
        } catch (e: Exception) {
            baseTicksText
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
