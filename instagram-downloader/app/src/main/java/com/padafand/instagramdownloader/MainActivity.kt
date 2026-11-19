package com.mediafetch.downloader

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import android.widget.LinearLayout
import android.view.ViewGroup
import android.widget.FrameLayout
import ir.tapsell.plus.AdRequestCallback
import ir.tapsell.plus.TapsellPlus
import ir.tapsell.plus.TapsellPlusBannerType
import ir.tapsell.plus.TapsellPlusInitListener
import ir.tapsell.plus.model.AdNetworkError
import ir.tapsell.plus.model.AdNetworks
import ir.tapsell.plus.model.TapsellPlusAdModel
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var bannerContainer: FrameLayout
    private var bannerResponseId: String? = null
    private val network = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var tapsellReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        webView = WebView(this)
        webView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        bannerContainer = FrameLayout(this).apply {
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (50 * resources.displayMetrics.density).toInt())
        }
        root.addView(webView)
        root.addView(bannerContainer)
        setContentView(root)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(DownloadBridge(this), "AndroidDownloads")
        webView.loadUrl("file:///android_asset/index.html")
        initializeTapsell()
    }

    private fun initializeTapsell() {
        val appKey = BuildConfig.TAPSELL_APP_KEY.trim()
        if (appKey.isEmpty()) {
            Log.e("MediaFetch", "TAPSELL_APP_KEY is not configured")
            return
        }
        TapsellPlus.initialize(this, appKey, object : TapsellPlusInitListener {
            override fun onInitializeSuccess(adNetworks: AdNetworks) {
                tapsellReady = true
                Log.i("MediaFetch", "Tapsell initialized: " + adNetworks.name)
                loadStandardBanner()
            }

            override fun onInitializeFailed(adNetworks: AdNetworks, error: AdNetworkError) {
                tapsellReady = false
                Log.e("MediaFetch", "Tapsell init failed: " + adNetworks.name + " " + error.errorMessage)
            }
        })
    }

    private fun loadStandardBanner() {
        if (!tapsellReady || bannerResponseId != null || isFinishing) return
        TapsellPlus.requestStandardBannerAd(
            this,
            "6aff7ca4728e2256843b0c95",
            TapsellPlusBannerType.BANNER_320x50,
            object : AdRequestCallback() {
                override fun response(ad: TapsellPlusAdModel) {
                    super.response(ad)
                    if (isFinishing || isDestroyed) return
                    bannerResponseId = ad.responseId
                    bannerContainer.visibility = android.view.View.VISIBLE
                    TapsellPlus.showStandardBannerAd(
                        this@MainActivity,
                        ad.responseId,
                        bannerContainer,
                        object : ir.tapsell.plus.AdShowListener() {
                            override fun onOpened(model: TapsellPlusAdModel) {
                                super.onOpened(model)
                                Log.i("MediaFetch", "Standard banner opened")
                            }
                            override fun onError(error: ir.tapsell.plus.model.TapsellPlusErrorModel) {
                                super.onError(error)
                                Log.e("MediaFetch", "Standard banner show failed: " + error.toString())
                                bannerContainer.visibility = android.view.View.GONE
                            }
                        }
                    )
                }
                override fun error(message: String) {
                    Log.e("MediaFetch", "Standard banner request failed: " + message)
                    runOnUiThread { bannerContainer.visibility = android.view.View.GONE }
                }
            }
        )
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
        fun save(url: String, suggestedName: String, mediaType: String): Long {
            return try {
                val uri = Uri.parse(url)
                require(uri.scheme == "https" || uri.scheme == "http") { "لینک دانلود معتبر نیست" }
                val cleanName = suggestedName.substringAfterLast('/')
                    .replace(Regex("[^\\\\p{L}\\\\p{N}._-]"), "_")
                    .take(100).ifBlank { "instagram_media" }
                    .let { name -> if (name.substringAfterLast('.', "").length in 1..5) name else name + if (mediaType == "photo") ".jpg" else ".mp4" }
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
            var localUri: String? = null
            var fileName = "instagram_media.mp4"
            if (cursor != null) {
                cursor.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val uriIndex = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val titleIndex = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
                        if (uriIndex >= 0) localUri = it.getString(uriIndex)
                        if (titleIndex >= 0) fileName = it.getString(titleIndex) ?: fileName
                        if (total > 0L) percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> { percent = 100; state = "publishing" }
                            DownloadManager.STATUS_FAILED -> state = "failed"
                        }
                    } else state = "failed"
                }
            } else state = "failed"

            if (state == "publishing") {
                val completedUri = localUri
                network.execute {
                    val published = try {
                        if (completedUri.isNullOrBlank()) false else publishToGallery(Uri.parse(completedUri), fileName)
                    } catch (e: Exception) {
                        Log.e("MediaFetch", "Gallery publishing failed", e)
                        false
                    }
                    mainHandler.post {
                        val finalState = if (published) "complete" else "gallery-failed"
                        webView.evaluateJavascript(
                            "window.onDownloadProgress(100, " + JSONObject.quote(finalState) + ")", null
                        )
                        if (!published) Toast.makeText(context, "فایل در Downloads ذخیره شد، اما افزودن آن به گالری ناموفق بود.", Toast.LENGTH_LONG).show()
                    }
                }
                webView.evaluateJavascript(
                    "window.onDownloadProgress(100, \"publishing\")", null
                )
                return
            }
            webView.evaluateJavascript(
                "window.onDownloadProgress(" + percent + ", " + JSONObject.quote(state) + ")", null
            )
            if (state == "running") mainHandler.postDelayed({ pollDownload(manager, id) }, 500)
        }

        private fun publishToGallery(source: Uri, fileName: String): Boolean {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            val mime = when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4", "m4v" -> "video/mp4"
                "mov" -> "video/quicktime"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                else -> context.contentResolver.getType(source) ?: "video/mp4"
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val isImage = mime.startsWith("image/")
                val collection = if (isImage) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val relativePath = (if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES) + "/MediaFetch"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val destination = context.contentResolver.insert(collection, values) ?: return false
                try {
                    val input = context.contentResolver.openInputStream(source) ?: return false
                    val output = context.contentResolver.openOutputStream(destination) ?: return false
                    input.use { src -> output.use { dst -> src.copyTo(dst) } }
                    val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    context.contentResolver.update(destination, ready, null, null)
                    return true
                } catch (e: Exception) {
                    context.contentResolver.delete(destination, null, null)
                    throw e
                }
            } else {
                val path = source.path ?: return false
                MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime), null)
                return true
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        network.shutdown()
        mainHandler.removeCallbacksAndMessages(null)
        if (bannerResponseId != null && ::bannerContainer.isInitialized) {
            try { TapsellPlus.destroyStandardBanner(this, bannerResponseId, bannerContainer) } catch (e: Exception) { Log.w("MediaFetch", "Banner destroy failed", e) }
        }
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
