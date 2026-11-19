package com.padafand.instagramdownloader

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val network = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(DownloadBridge(this), "AndroidDownloads")
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class DownloadBridge(private val context: Context) {
        @JavascriptInterface
        fun process(postUrl: String, apiBase: String, authToken: String) {
            network.execute {
                val result = try {
                    val source = Uri.parse(postUrl)
                    val endpoint = Uri.parse(apiBase)
                    require(source.scheme == "https" && (source.host == "instagram.com" || source.host?.endsWith(".instagram.com") == true)) {
                        "لینک اینستاگرام معتبر نیست."
                    }
                    require(endpoint.scheme == "https" && !endpoint.host.isNullOrBlank()) {
                        "نشانی سرویس باید HTTPS باشد."
                    }
                    val endpointText = apiBase.trimEnd('/') + "/"
                    val connection = (URL(endpointText).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 20000
                        readTimeout = 60000
                        doOutput = true
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "application/json")
                        if (authToken.isNotBlank()) setRequestProperty("Authorization", authToken.trim())
                    }
                    try {
                        val payload = JSONObject()
                            .put("url", postUrl)
                            .put("videoQuality", "720")
                            .put("downloadMode", "auto")
                            .put("filenameStyle", "pretty")
                            .toString()
                        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                        val code = connection.responseCode
                        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        if (code !in 200..299) throw Exception("خطای سرویس ($code): " + body.take(220))
                        JSONObject(body).toString()
                    } finally {
                        connection.disconnect()
                    }
                } catch (e: Exception) {
                    JSONObject().put("status", "client-error")
                        .put("message", e.message ?: "اتصال ناموفق بود.").toString()
                }
                val quoted = JSONObject.quote(result)
                runOnUiThread {
                    webView.evaluateJavascript("window.onNativeResult($quoted)", null)
                }
            }
        }

        @JavascriptInterface
        fun save(url: String, suggestedName: String) {
            runOnUiThread {
                try {
                    val uri = Uri.parse(url)
                    require(uri.scheme == "https" || uri.scheme == "http") { "لینک دانلود معتبر نیست" }
                    val cleanName = suggestedName
                        .substringAfterLast('/')
                        .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
                        .take(100)
                        .ifBlank { "instagram_media.mp4" }
                    val request = DownloadManager.Request(uri)
                        .setTitle(cleanName)
                        .setDescription("در حال ذخیره فایل")
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, cleanName)
                        .setAllowedOverMetered(true)
                        .setAllowedOverRoaming(false)
                    val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    manager.enqueue(request)
                    Toast.makeText(context, "دانلود به پوشه Download اضافه شد", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, e.message ?: "شروع دانلود ناموفق بود", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        network.shutdown()
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
