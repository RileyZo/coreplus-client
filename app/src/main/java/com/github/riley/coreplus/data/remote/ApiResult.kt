package com.github.riley.coreplus.data.remote

import com.github.riley.coreplus.domain.error.AppError

/**
 * 网络调用的结果包装。
 *
 * 不用 `kotlin.Result` 有两个原因：
 * - `Result` 是 inline class，作为返回类型时无法携带密封类的类型信息，`when` 分支不够直观
 * - 失败分支需要携带结构化的 [AppError]，而不是任意 `Throwable`
 *
 * [CorePlusApi] 的每个方法都返回本类型，**不向外抛业务异常**，
 * 因此 Repository 层不需要写 try/catch，也不会因为漏捕获而崩溃。
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val data: T) : ApiResult<T>

    /** 失败。[error] 已完成从 HTTP 状态码 / 后端错误码到领域错误的映射。 */
    data class Error(val error: AppError) : ApiResult<Nothing>
}

/** 成功时取数据，失败时返回 null。 */
fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data

/** 成功时返回 null，失败时返回错误。 */
fun <T> ApiResult<T>.errorOrNull(): AppError? = (this as? ApiResult.Error)?.error
