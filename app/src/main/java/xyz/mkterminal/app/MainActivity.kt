package xyz.mkterminal.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
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

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()

                if (url.contains("/api/")) {
                    val apiPath = url.substring(url.indexOf("/api/"))
                    val targetUrl = remoteApiBase + apiPath
                    return try {
                        val reqBuilder = Request.Builder().url(targetUrl)
                        for ((key, value) in request.requestHeaders) {
                            if (!key.equals("Host", ignoreCase = true)) {
                                reqBuilder.addHeader(key, value)
                            }
                        }
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
                        e.printStackTrace()
                        assetLoader.shouldInterceptRequest(request.url)
                    }
                }

                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
