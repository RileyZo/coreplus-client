package com.github.riley.coreplus.domain.error

/**
 * 应用内统一的错误类型。
 *
 * 领域层与 UI 层只认这个类型，不认 Ktor 异常、`IOException` 或后端错误码字符串——
 * 映射工作在 `data/remote` 的异常翻译层完成，映射规则见 `docs/API.md` §3.2 的错误码表。
 *
 * 本接口**不依赖 Android**（不含 `@StringRes`），因此可以被纯 JVM 单元测试直接构造与断言。
 * 面向用户的文案在 UI 层用穷尽 `when` 映射到字符串资源，新增分支时编译器会强制提醒补文案。
 */
sealed interface AppError {

    // region 网络与传输

    /** 连不上后端：DNS 失败、连接被拒、无网络。 */
    data object Unreachable : AppError

    /** 请求超时。拉取整学期课表可能很慢，超时阈值见 `di/NetworkModule`。 */
    data object Timeout : AppError

    /** 响应体无法解析成预期的 JSON 结构（含后端返回了 HTML 错误页的情况）。 */
    data object MalformedResponse : AppError

    // endregion

    // region 认证（对应 API.md 的 401 家族）

    /**
     * 验证码错误或已过期，对应后端的 `CAPTCHA_INVALID` / `CAPTCHA_EXPIRED`。
     *
     * 由于 `captchaId` 是一次性的，UI 收到本错误后**必须重新拉取验证码**并清空输入框。
     */
    data class CaptchaRejected(val serverMessage: String?) : AppError

    /**
     * 学号或密码错误，对应后端的 `CREDENTIALS_INVALID`。
     *
     * 学校侧验证码在校验一次后即作废，因此 UI 同样需要重新拉取验证码。
     */
    data class CredentialsRejected(val serverMessage: String?) : AppError

    /**
     * 需要重新登录，对应后端的 `TOKEN_EXPIRED` / `TOKEN_INVALID` / `UPSTREAM_SESSION_LOST`，
     * 以及本地根本没有 token 却尝试拉取课表的情况。
     *
     * 三者对用户而言动作一致（重新登录），故合并为一个分支；具体原因保留在 [reason] 中便于排查。
     */
    data class LoginRequired(val reason: Reason = Reason.TOKEN_EXPIRED) : AppError {
        enum class Reason { TOKEN_EXPIRED, TOKEN_INVALID, UPSTREAM_SESSION_LOST, NO_TOKEN }
    }

    // endregion

    // region 后端与学校系统

    /** 请求参数非法，对应 `400 INVALID_REQUEST`。 */
    data class BadRequest(val serverMessage: String?) : AppError

    /**
     * 触发限流，对应 `429 RATE_LIMITED`。
     *
     * @property retryAfterSeconds 后端 `Retry-After` 响应头给出的等待秒数，缺省时为 null
     */
    data class RateLimited(val retryAfterSeconds: Int?) : AppError

    /** 学校教务系统不可达或超时，对应 `502/504 UPSTREAM_UNAVAILABLE`。 */
    data class UpstreamUnavailable(val serverMessage: String?) : AppError

    /**
     * 后端自身异常（`500 INTERNAL_ERROR`）、未列在契约中的错误码，以及其他未归类的 HTTP 状态码。
     *
     * @property status HTTP 状态码；无法取得时为 null
     * @property code 后端错误信封中的 `code`，可能为 null 或契约外的值
     */
    data class Server(val status: Int?, val code: String?, val serverMessage: String?) : AppError

    // endregion

    // region 本地

    /** 本地缓存读写失败（磁盘满、权限异常等）。 */
    data class Storage(val cause: Throwable?) : AppError

    // endregion
}

/** 该错误是否意味着「必须重新登录才能继续」。 */
val AppError.requiresRelogin: Boolean
    get() = this is AppError.LoginRequired

/**
 * 该错误是否意味着「本次验证码已作废，重试前必须换一张图」。
 *
 * 依据 `docs/API.md` §4.2：`captchaId` 一次性，无论登录成功或失败都会被消费掉。
 */
val AppError.requiresNewCaptcha: Boolean
    get() = this is AppError.CaptchaRejected || this is AppError.CredentialsRejected
