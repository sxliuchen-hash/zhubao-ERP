package com.zhubao.erp.shell

import java.net.URI

/** 容器里不依赖界面、可以单测的地址和错误判断。 */
object ShellPolicies {

    data class HttpsOrigin(val host: String, val port: String, val pageUrl: String)

    fun parseHttpsOrigin(raw: String?): HttpsOrigin? {
        val text = raw?.trim().orEmpty()
        if (!text.startsWith("https://")) return null
        val uri = try {
            URI(text)
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.trim().orEmpty()
        if (host.isEmpty() || uri.userInfo != null) return null
        val port = if (uri.port > 0) ":${uri.port}" else ""
        return HttpsOrigin(host, port, text)
    }

    fun mainFrameError(statusCode: Int): Pair<String, String>? = when (statusCode) {
        401 -> "登录已失效" to "请重新登录后再继续作业。"
        500 -> "服务暂时不可用" to "服务器处理失败（500）。请稍后重试，不要把这个提示当成已经完成作业。"
        503 -> "服务暂时不可用" to "服务器正在维护（503）。请稍后重试，不要把这个提示当成已经完成作业。"
        else -> null
    }

    /** 已授权直接拍照；拒绝后可再次申请；永久拒绝则去系统设置。 */
    fun cameraNextStep(granted: Boolean, askedBefore: Boolean, shouldShowRationale: Boolean): String {
        if (granted) return "CAPTURE"
        if (!askedBefore) return "REQUEST"
        if (shouldShowRationale) return "EXPLAIN_AND_REQUEST"
        return "OPEN_SETTINGS"
    }

    /** 根据网页文件输入的 MIME 类型选择系统拍摄动作。 */
    fun captureKinds(acceptTypes: Array<String>): List<String> {
        val types = acceptTypes.flatMap { it.split(',') }.map { it.trim().lowercase() }
        val image = types.any { it == "image/*" || it.startsWith("image/") }
        val video = types.any { it == "video/*" || it.startsWith("video/") }
        return buildList {
            if (image) add("image")
            if (video) add("video")
        }
    }
}
