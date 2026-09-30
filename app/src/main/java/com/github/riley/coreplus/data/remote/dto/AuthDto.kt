package com.github.riley.coreplus.data.remote.dto

import com.github.riley.coreplus.domain.repository.LoginCredentials
import kotlinx.serialization.Serializable

/**
 * `GET /api/auth/captcha` 的响应，见 `docs/API.md` §4.1。
 *
 * 验证码图片由后端从学校**原样透传**，以 Base64 内联在 JSON 中，
 * 这样只需一次往返，且不必把学校的会话 cookie 暴露给客户端。
 */
@Serializable
data class CaptchaResponseDto(
    /** 一次性句柄，登录时必须原样回传。 */
    val captchaId: String,
    /** 图片原始字节的 Base64，不含 `data:` 前缀、不换行。 */
    val imageBase64: String,
    /** 图片 MIME 类型，仅供排查；客户端用 `BitmapFactory` 解码，不依赖此字段。 */
    val imageMediaType: String? = null,
    /** 有效期（秒）。 */
    val expiresIn: Int? = null,
)

/**
 * `POST /api/auth/login` 的请求体，见 `docs/API.md` §4.2。
 *
 * 刻意**不重写** [toString]——`data class` 的默认实现会把明文密码打出来。
 * 网络日志已通过 `Logging.filter` 排除本请求（见 `di/NetworkModule`），
 * 这里再让密码无法被 `toString` 意外泄露，作为第二道防线。
 */
@Serializable
data class LoginRequestDto(
    val studentId: String,
    val password: String,
    val captchaId: String,
    val captchaCode: String,
) {
    override fun toString(): String =
        "LoginRequestDto(studentId=$studentId, password=***, captchaId=$captchaId, captchaCode=***)"
}

/**
 * `POST /api/auth/login` 的响应，见 `docs/API.md` §4.2。
 *
 * `token` 是后端所持**已认证学校会话**的句柄，对客户端不透明。
 * 除 [token] 外的字段都是可选的：后端拿不到学生姓名时直接省略即可，客户端不会失败。
 */
@Serializable
data class LoginResponseDto(
    val token: String,
    /** 固定为 `"Bearer"`，客户端不校验，仅为符合 RFC 6750 而保留。 */
    val tokenType: String? = null,
    /** 有效期（秒），约 3600。 */
    val expiresIn: Int? = null,
    /** 回显学号，供客户端核对。 */
    val studentId: String? = null,
    /** 学生姓名，学校若返回则透传。 */
    val name: String? = null,
)

/** 领域层的登录表单 → 请求 DTO。 */
fun LoginCredentials.toDto(): LoginRequestDto = LoginRequestDto(
    studentId = studentId.trim(),
    password = password,
    captchaId = captchaId,
    captchaCode = captchaCode.trim(),
)
