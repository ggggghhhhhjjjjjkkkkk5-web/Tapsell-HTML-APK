package com.padafand.instagramdownloader

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

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
                    val connection = (URL(apiBase.trimEnd('/') + "/").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 12000
                        readTimeout = 45000
                        doOutput = true
                        useCaches = false
                        setRequestProperty("Accept", "application/json")
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Connection", "keep-alive")
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
                runOnUiThread { webView.evaluateJavascript("window.onNativeResult($quoted)", null) }
            }
        }

        @JavascriptInterface
        fun save(url: String, suggestedName: String): Long {
            return try {
                val uri = Uri.parse(url)
                require(uri.scheme == "https" || uri.scheme == "http") { "لینک دانلود معتبر نیست" }
                val cleanName = suggestedName.substringAfterLast('/')
                    .replace(Regex("[^\\p{L}\\p{N}._-]"), "_")
                    .take(100).ifBlank { "instagram_media.mp4" }
                val request = DownloadManager.Request(uri)
                    .setTitle(cleanName)
                    .setDescription("دانلود رسانه اینستاگرام")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, cleanName)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val id = manager.enqueue(request)
                mainHandler.post { pollDownload(manager, id) }
                id
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(context, e.message ?: "شروع دانلود ناموفق بود", Toast.LENGTH_LONG).show() }
                -1L
            }
        }

        private fun pollDownload(manager: DownloadManager, id: Long) {
            val cursor = manager.query(DownloadManager.Query().setFilterById(id))
            var percent = 0
            var state = "running"
            if (cursor != null) {
                cursor.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        if (total > 0L) percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> { percent = 100; state = "complete" }
                            DownloadManager.STATUS_FAILED -> state = "failed"
                        }
                    } else state = "failed"
                }
            } else state = "failed"
            webView.evaluateJavascript(
                "window.onDownloadProgress(" + percent + ", " + JSONObject.quote(state) + ")", null
            )
            if (state == "running") mainHandler.postDelayed({ pollDownload(manager, id) }, 500)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        network.shutdown()
        mainHandler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}