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
import java.net.URLDecoder

class MainActivity : Activity() {
    private lateinit var webView: WebView

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
        fun save(url: String, suggestedName: String) {
            runOnUiThread {
                try {
                    val uri = Uri.parse(url)
                    if (uri.scheme != "https" && uri.scheme != "http") {
                        Toast.makeText(context, "لینک دانلود معتبر نیست", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
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
                    Toast.makeText(context, "شروع دانلود ناموفق بود", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
