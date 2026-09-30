package com.github.riley.coreplus.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 后端统一的错误信封，见 `docs/API.md` §3.1。
 *
 * 两个字段都设为可空且带默认值：网关或反向代理可能返回结构不符的错误体
 * （甚至 HTML），此时解析结果为 null，调用方按 HTTP 状态码退化处理，
 * 绝不能因为「错误响应本身解析失败」而抛出新的异常。
 */
@Serializable
data class ApiErrorDto(
    /** 机器可读错误码，取值见 API.md §3.2。 */
    val code: String? = null,
    /** 面向用户的中文提示，网络类错误下会直接展示给用户。 */
    val message: String? = null,
)
