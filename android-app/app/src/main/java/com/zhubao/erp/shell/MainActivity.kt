package com.zhubao.erp.shell

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.provider.Settings
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * 只加载本机配置的 ERP HTTPS 地址。证书错误中止连接，不向网页暴露原生桥。
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
    private var pendingCapture: (() -> Unit)? = null
    private var cameraAsked = false
    private var captureOutputs: List<CaptureOutput> = emptyList()
    private var keepErrorVisible = false
    private var retryAction: () -> Unit = { loadErp() }
    private val allowedOrigin = ShellOrigin.parse(BuildConfig.ERP_BASE_URL)
    private data class CaptureOutput(val uri: Uri, val file: File)

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback
        fileCallback = null
        val parsed = if (result.resultCode == RESULT_OK) FileChooserParams.parseResult(result.resultCode, result.data) else null
        val captured = if (result.resultCode == RESULT_OK) captureOutputs.firstOrNull { it.file.length() > 0L }?.uri else null
        val selected = captured?.let { arrayOf(it) } ?: parsed?.takeIf { it.isNotEmpty() }
        callback?.onReceiveValue(selected)
        clearCaptureOutputs(selected?.toSet().orEmpty())
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val capture = pendingCapture
        if (capture != null) {
            if (granted) {
                pendingCapture = null
                hideError()
                capture()
            } else {
                explainCameraDenial()
            }
        }
    }

    private val webMediaPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val request = pendingWebPermission
        pendingWebPermission = null
        if (request != null) {
            val resources = safeMediaResources(request)
            if (allowedOrigin?.matches(request.origin) == true && allowedOrigin?.matches(webView.url) == true &&
                resources.isNotEmpty() && canGrantWebResources(resources)
            ) {
                request.grant(resources)
            } else {
                request.deny()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.erp_view)
        errorPanel = findViewById(R.id.error_panel)
        errorTitle = findViewById(R.id.error_title)
        errorBody = findViewById(R.id.error_body)
        findViewById<Button>(R.id.error_retry).setOnClickListener { retryAction() }
        configureWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    errorPanel.visibility == View.VISIBLE -> {
                        hideError()
                        cancelFileChooser()
                    }
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
        // 登录令牌写在网页 localStorage。启动时不要清缓存或存储，否则每次打开都要重新登录。
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
        webView.setDownloadListener { url, _, _, _, _ ->
            val target = Uri.parse(url)
            if (allowedOrigin?.matches(target) == true &&
                target.path?.matches(Regex("/api/documents/attachments/[^/]+/content")) == true &&
                target.getQueryParameter("download") == "true" &&
                !target.getQueryParameter("token").isNullOrBlank() &&
                !target.getQueryParameter("expiresAt").isNullOrBlank()
            ) {
                openExternal(target)
            } else {
                showError("下载地址无效", "请返回证据清单重新点击下载。")
            }
        }
    }

    private fun loadErp() {
        hideError()
        keepErrorVisible = false
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

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            showError("无法打开链接", "系统里没有能打开该链接的应用。")
        }
    }

    private fun showError(title: String, body: String, retry: () -> Unit = { loadErp() }) {
        clearTimeout()
        keepErrorVisible = true
        retryAction = retry
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

    private fun canGrantWebResources(resources: Array<String>): Boolean {
        return resources.all { resource ->
            val permission = when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                else -> return@all false
            }
            ContextCompat.checkSelfPermission(this, permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        clearTimeout()
        pendingWebPermission?.deny()
        pendingWebPermission = null
        cancelFileChooser()
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
            val message = ShellPolicies.mainFrameError(errorResponse?.statusCode ?: 0) ?: return
            showError(message.first, message.second)
        }
    }

    private inner class ErpChromeClient : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest?) {
            if (request == null) return
            val resources = safeMediaResources(request)
            if (resources.isEmpty() || resources.size != request.resources.size ||
                allowedOrigin?.matches(request.origin) != true || allowedOrigin?.matches(webView.url) != true
            ) {
                request.deny()
                return
            }
            if (!canGrantWebResources(resources)) {
                pendingWebPermission?.deny()
                pendingWebPermission = request
                val needed = resources.mapNotNull { resource ->
                    when (resource) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                        else -> null
                    }
                }.filter { permission ->
                    ContextCompat.checkSelfPermission(this@MainActivity, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED
                }.distinct()
                webMediaPermissionLauncher.launch(needed.toTypedArray())
                return
            }
            request.grant(resources)
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
            if (pendingWebPermission == request) pendingWebPermission = null
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            cancelFileChooser()
            if (allowedOrigin?.matches(webView?.url) != true) {
                filePathCallback?.onReceiveValue(null)
                return false
            }
            fileCallback = filePathCallback
            val contentIntent = try {
                fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
            } catch (_: ActivityNotFoundException) {
                fileCallback = null
                filePathCallback?.onReceiveValue(null)
                return false
            }
            if (fileChooserParams?.isCaptureEnabled == true) {
                val kinds = ShellPolicies.captureKinds(fileChooserParams.acceptTypes)
                if (kinds.isEmpty()) return launchChooser(Intent.createChooser(contentIntent, "选择文件"))
                ensureCamera {
                    try {
                        val captures = kinds.map { kind -> createCaptureIntent(kind) }
                        val intent = if (captures.size == 1) {
                            captures.single()
                        } else {
                            Intent.createChooser(contentIntent, "选择拍摄方式").apply {
                                putExtra(Intent.EXTRA_INITIAL_INTENTS, captures.toTypedArray())
                            }
                        }
                        launchChooser(intent)
                    } catch (_: java.io.IOException) {
                        cancelFileChooser()
                        showError("无法启动拍摄", "无法创建临时拍摄文件，请检查设备存储空间后重试。")
                    }
                }
                return true
            }
            return launchChooser(Intent.createChooser(contentIntent, "选择文件"))
        }

        override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
            result?.confirm()
            return true
        }
    }

    private fun createCaptureIntent(kind: String): Intent {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        // 已交给网页的文件可能仍在上传，只清理一天前的拍摄缓存。
        dir.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 24L * 60L * 60L * 1000L }
            ?.forEach { it.delete() }
        val extension = if (kind == "video") "mp4" else "jpg"
        val file = File.createTempFile("capture-", ".$extension", dir)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        captureOutputs = captureOutputs + CaptureOutput(uri, file)
        val action = if (kind == "video") MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE
        return Intent(action).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            if (kind == "video") {
                putExtra(MediaStore.EXTRA_DURATION_LIMIT, 30)
                putExtra(MediaStore.EXTRA_SIZE_LIMIT, 50L * 1024L * 1024L)
            }
            clipData = ClipData.newUri(contentResolver, "拍摄文件", uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun ensureCamera(onGranted: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val rationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
        when (ShellPolicies.cameraNextStep(granted, cameraAsked, rationale)) {
            "CAPTURE" -> onGranted()
            "REQUEST" -> {
                cameraAsked = true
                pendingCapture = onGranted
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            "EXPLAIN_AND_REQUEST" -> showError("需要相机权限", "拍照前要允许使用相机。点重试会再次申请。") {
                hideError()
                pendingCapture = onGranted
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                pendingCapture = onGranted
                showError("相机权限被关闭", "请到系统设置里允许相机，返回应用后点重试继续拍摄。") {
                    openAppSettings()
                }
            }
        }
    }

    private fun explainCameraDenial() {
        val rationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)
        if (rationale) {
            showError("相机权限被拒绝", "没有相机权限就不能直接拍摄。点重试可以再次申请。") {
                hideError()
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            showError("相机权限被关闭", "请到系统设置里允许相机，返回应用后点重试继续拍摄。") {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    hideError()
                    val capture = pendingCapture
                    pendingCapture = null
                    capture?.invoke()
                } else {
                    openAppSettings()
                }
            }
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun launchChooser(chooser: Intent): Boolean {
        return try {
            fileChooserLauncher.launch(chooser)
            true
        } catch (_: ActivityNotFoundException) {
            cancelFileChooser()
            false
        }
    }

    private fun clearCaptureOutputs(keep: Set<Uri> = emptySet()) {
        captureOutputs.forEach { output -> if (output.uri !in keep) output.file.delete() }
        captureOutputs = emptyList()
    }

    private fun cancelFileChooser() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        pendingCapture = null
        clearCaptureOutputs()
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
