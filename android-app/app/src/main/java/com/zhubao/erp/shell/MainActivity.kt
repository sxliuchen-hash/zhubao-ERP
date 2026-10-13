package com.zhubao.erp.shell

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.view.View
import android.webkit.WebChromeClient.FileChooserParams
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * 只加载本机配置的 ERP HTTPS 地址。证书错误中止连接，原生桥不向其他来源开放。
 */
class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var errorPanel: View
    private lateinit var errorTitle: TextView
    private lateinit var errorBody: TextView
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pageTimeout: Runnable? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PermissionRequest? = null
    private var cameraOutput: Uri? = null
    private var keepErrorVisible = false
    private val allowedOrigin = ShellOrigin.parse(BuildConfig.ERP_BASE_URL)

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback
        fileCallback = null
        if (callback == null) return@registerForActivityResult
        val parsed = FileChooserParams.parseResult(result.resultCode, result.data)
        if (parsed != null && parsed.isNotEmpty()) {
            callback.onReceiveValue(parsed)
            return@registerForActivityResult
        }
        val captured = cameraOutput
        if (result.resultCode == RESULT_OK && captured != null) {
            callback.onReceiveValue(arrayOf(captured))
        } else {
            callback.onReceiveValue(null)
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val request = pendingWebPermission
        pendingWebPermission = null
        if (request == null) return@registerForActivityResult
        if (granted && allowedOrigin?.matches(request.origin) == true) {
            request.grant(safeMediaResources(request))
        } else {
            request.deny()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.erp_view)
        errorPanel = findViewById(R.id.error_panel)
        errorTitle = findViewById(R.id.error_title)
        errorBody = findViewById(R.id.error_body)
        findViewById<Button>(R.id.error_retry).setOnClickListener { loadErp() }
        configureWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    errorPanel.visibility == View.VISIBLE -> hideError()
                    webView.canGoBack() -> webView.goBack()
                    else -> finish()
                }
            }
        })
        loadErp()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = settings.userAgentString + " ZhubaoErpShell/" + BuildConfig.VERSION_NAME
        webView.webViewClient = ErpWebViewClient()
        webView.webChromeClient = ErpChromeClient()
    }

    private fun loadErp() {
        hideError()
        keepErrorVisible = false
        detachBridge()
        val origin = allowedOrigin
        if (origin == null) {
            showError("尚未配置地址", "请在 android-app/local.properties 设置 erp.baseUrl，且必须以 https:// 开头。不要把密码写进工程。")
            return
        }
        if (!isOnline()) {
            showError("网络不可用", "现场作业需要保持联网。请连接网络后重试。")
            return
        }
        armTimeout()
        webView.loadUrl(origin.pageUrl)
    }

    private fun armTimeout() {
        pageTimeout?.let { mainHandler.removeCallbacks(it) }
        val timeout = Runnable {
            showError("连接超时", "服务器在 30 秒内没有响应。请确认地址和网络后重试。")
            webView.stopLoading()
        }
        pageTimeout = timeout
        mainHandler.postDelayed(timeout, 30_000)
    }

    private fun clearTimeout() {
        pageTimeout?.let { mainHandler.removeCallbacks(it) }
        pageTimeout = null
    }

    private fun attachBridgeIfAllowed(url: String?) {
        val origin = allowedOrigin
        if (origin != null && origin.matches(url)) {
            webView.addJavascriptInterface(ShellBridge(), BRIDGE_NAME)
        } else {
            detachBridge()
        }
    }

    private fun detachBridge() {
        webView.removeJavascriptInterface(BRIDGE_NAME)
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            showError("无法打开链接", "系统里没有能打开该链接的应用。")
        }
    }

    private fun showError(title: String, body: String) {
        clearTimeout()
        keepErrorVisible = true
        detachBridge()
        errorTitle.text = title
        errorBody.text = body
        errorPanel.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorPanel.visibility = View.GONE
    }

    private fun isOnline(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun safeMediaResources(request: PermissionRequest): Array<String> {
        return request.resources.filter { resource ->
            resource == PermissionRequest.RESOURCE_VIDEO_CAPTURE || resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }.toTypedArray()
    }

    override fun onDestroy() {
        clearTimeout()
        detachBridge()
        webView.destroy()
        super.onDestroy()
    }

    private inner class ErpWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val target = request?.url ?: return false
            val origin = allowedOrigin
            if (origin != null && origin.matches(target)) {
                return false
            }
            openExternal(target)
            return true
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            attachBridgeIfAllowed(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            clearTimeout()
            if (!keepErrorVisible && allowedOrigin?.matches(url) == true) {
                hideError()
            }
        }

        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            handler?.cancel()
            showError("证书错误", "服务器证书不受信任，连接已中止。请更换受信任证书后再访问，不能忽略该错误。")
        }

        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame != true) return
            val offline = error?.errorCode == ERROR_HOST_LOOKUP || error?.errorCode == ERROR_CONNECT || error?.errorCode == ERROR_TIMEOUT
            if (offline) {
                showError("网络不可用", "无法连接服务器。请检查网络后重试。")
            } else {
                showError("页面打开失败", error?.description?.toString() ?: "请稍后重试。")
            }
        }

        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
            if (request?.isForMainFrame != true) return
            if (errorResponse?.statusCode == 401) {
                showError("登录已失效", "请重新登录后再继续作业。")
            }
        }
    }

    private inner class ErpChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest?) {
            if (request == null) return
            val resources = safeMediaResources(request)
            if (resources.isEmpty() || allowedOrigin?.matches(request.origin) != true) {
                request.deny()
                return
            }
            val needsCamera = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
            if (needsCamera && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingWebPermission = request
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                return
            }
            request.grant(resources)
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            val contentIntent = try {
                fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            } catch (_: ActivityNotFoundException) {
                fileCallback = null
                filePathCallback?.onReceiveValue(null)
                return false
            }
            val chooser = Intent.createChooser(contentIntent, "选择文件")
            if (fileChooserParams?.isCaptureEnabled == true) {
                cameraOutput = createCameraUri()
                val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                capture.putExtra(MediaStore.EXTRA_OUTPUT, cameraOutput)
                capture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(capture))
            } else {
                cameraOutput = null
            }
            return try {
                fileChooserLauncher.launch(chooser)
                true
            } catch (_: ActivityNotFoundException) {
                fileCallback = null
                filePathCallback?.onReceiveValue(null)
                false
            }
        }

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            result?.confirm()
            return true
        }
    }

    private fun createCameraUri(): Uri {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    private inner class ShellBridge {
        @android.webkit.JavascriptInterface
        fun appVersion(): String = BuildConfig.VERSION_NAME

        @android.webkit.JavascriptInterface
        fun notifySessionExpired() {
            runOnUiThread { showError("登录已失效", "请重新登录后再继续作业。") }
        }
    }

    companion object {
        private const val BRIDGE_NAME = "ZhubaoShell"
    }
}

/** 只接受一个 https 来源。其他链接交给系统浏览器。 */
internal object ShellOrigin {
    fun parse(raw: String?): Allowed? {
        val text = raw?.trim().orEmpty()
        if (!text.startsWith("https://")) return null
        val uri = Uri.parse(text)
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty() || uri.userInfo != null) return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return Allowed(host, port, text)
    }

    internal class Allowed(
        private val host: String,
        private val port: String,
        val pageUrl: String
    ) {
        fun matches(url: String?): Boolean = matches(if (url.isNullOrBlank()) null else Uri.parse(url))

        fun matches(uri: Uri?): Boolean {
            if (uri == null || !uri.scheme.equals("https", ignoreCase = true)) return false
            if (!uri.host.equals(host, ignoreCase = true)) return false
            val actualPort = if (uri.port > 0) ":${uri.port}" else ""
            return actualPort == port
        }
    }
}
