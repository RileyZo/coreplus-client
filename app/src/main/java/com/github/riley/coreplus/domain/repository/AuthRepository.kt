package com.github.riley.coreplus.domain.repository

import com.github.riley.coreplus.domain.error.AppError

/**
 * 认证仓库：验证码获取 + 登录。
 *
 * 完整契约见 `docs/API.md` §4.1、§4.2。学校教务系统是两段式的
 * （先取验证码图片、再连同账号密码一起提交），后端代为持有学校会话，
 * 并把该会话的句柄以 token 形式下发。
 *
 * **token 与凭据均不落盘**（技术设计文档 §1「不允许缓存账号密码；token 有效期约 1 小时，不缓存」）：
 * token 只存内存单例，学号与密码只存在于 Compose 状态、对话框关闭即清空。
 */
interface AuthRepository {

    /** 获取一张新验证码。每次调用都会让后端向学校请求新图，旧的 `captchaId` 随即失效。 */
    suspend fun captcha(): CaptchaResult

    /**
     * 提交登录。成功后 token 被写入内存 [com.github.riley.coreplus.data.remote.TokenStore]，
     * 之后 [ScheduleRepository.refresh] 才能工作。
     *
     * @return 失败原因；成功时返回 null
     */
    suspend fun login(credentials: LoginCredentials): AppError?

    /** 清空内存中的 token。刷新流程开始时与收到 401 时调用。 */
    fun logout()

    /** 内存中是否已有可用 token。 */
    fun hasToken(): Boolean
}

/**
 * 登录表单的一次提交内容。
 *
 * 刻意**不实现** [toString]，避免密码被意外打进日志。
 */
class LoginCredentials(
    val studentId: String,
    val password: String,
    val captchaId: String,
    val captchaCode: String,
) {
    override fun toString(): String = "LoginCredentials(studentId=$studentId, password=***, captchaId=$captchaId, captchaCode=***)"
}

/** [AuthRepository.captcha] 的结果。 */
sealed interface CaptchaResult {

    /**
     * 取到验证码。
     *
     * 刻意不用 `data class`：[imageBytes] 是 [ByteArray]，
     * 自动生成的 `equals`/`hashCode` 只比较引用，会产生误导（编译器也会告警）。
     *
     * @property captchaId 登录时必须原样回传的一次性句柄
     * @property imageBytes 图片原始字节，UI 层用 `BitmapFactory` 解码，格式无关
     * @property expiresInSeconds 有效期秒数，后端未提供时为 null
     */
    class Success(
        val captchaId: String,
        val imageBytes: ByteArray,
        val expiresInSeconds: Int?,
    ) : CaptchaResult

    /** 取验证码失败。UI 应展示错误并提供「点击重试」。 */
    data class Failure(val error: AppError) : CaptchaResult
}
