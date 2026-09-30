package com.github.riley.coreplus.ui.common

import android.content.Context
import com.github.riley.coreplus.R
import com.github.riley.coreplus.domain.error.AppError

/**
 * 把 [AppError] 翻译成面向用户的中文提示。
 *
 * 放在 UI 层而非领域层，是为了让 `AppError` 保持**零 Android 依赖**，
 * 从而能被纯 JVM 单元测试直接构造与断言。
 *
 * `when` 是穷尽的：将来给 [AppError] 增加分支时，编译器会强制在这里补上文案。
 *
 * 优先使用后端返回的 `serverMessage`（错误信封里的 `message`），
 * 因为后端往往能给出更具体的原因（例如「学号不存在」vs「密码错误」）；
 * 缺失时才回退到客户端内置文案。
 */
fun AppError.toUserMessage(context: Context): String = when (this) {
    AppError.Unreachable -> context.getString(R.string.error_unreachable)
    AppError.Timeout -> context.getString(R.string.error_timeout)
    AppError.MalformedResponse -> context.getString(R.string.error_malformed_response)

    is AppError.CaptchaRejected ->
        serverMessage ?: context.getString(R.string.error_captcha)

    is AppError.CredentialsRejected ->
        serverMessage ?: context.getString(R.string.error_credentials)

    // 三种原因（token 过期 / token 非法 / 学校会话丢失）对用户而言动作一致，故合并文案
    is AppError.LoginRequired -> context.getString(R.string.error_login_required)

    is AppError.BadRequest ->
        serverMessage ?: context.getString(R.string.error_bad_request)

    is AppError.RateLimited -> retryAfterSeconds
        ?.takeIf { it > 0 }
        ?.let { context.getString(R.string.error_rate_limited_with_seconds, it) }
        ?: context.getString(R.string.error_rate_limited)

    is AppError.UpstreamUnavailable ->
        serverMessage ?: context.getString(R.string.error_upstream_unavailable)

    is AppError.Server -> when {
        serverMessage != null -> serverMessage
        status != null -> context.getString(R.string.error_server_with_status, status)
        else -> context.getString(R.string.error_server)
    }

    is AppError.Storage -> context.getString(R.string.error_storage)
}
