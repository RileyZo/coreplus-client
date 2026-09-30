package com.github.riley.coreplus.data.remote

/**
 * 后端接口地址与超时配置。
 *
 * 完整契约见 `docs/API.md`。**后端换地址时只需改 [BASE_URL] 这一行。**
 *
 * 关于 HTTP 明文：开发期后端跑在局域网，debug 构建通过
 * `app/src/debug/res/xml/network_security_config.xml` 放行明文流量；
 * release 构建默认禁止明文，上线 HTTPS 后无需改动本文件。
 */
object ApiConfig {

    /** 后端根地址，不含结尾斜杠，不带版本前缀。 */
    const val BASE_URL: String = "http://192.168.110.72:4326/api"

    // region 路径（相对 BASE_URL）

    /** `GET` 获取验证码，见 API.md §4.1。 */
    const val PATH_CAPTCHA: String = "auth/captcha"

    /** `POST` 登录，见 API.md §4.2。 */
    const val PATH_LOGIN: String = "auth/login"

    /** `GET` 获取整学期课表，见 API.md §4.3。 */
    const val PATH_SCHEDULE: String = "schedule"

    /** `POST` 登出，见 API.md §4.4。客户端当前不调用，仅保留常量。 */
    const val PATH_LOGOUT: String = "auth/logout"

    // endregion

    // region 超时

    /** 建连超时。 */
    const val CONNECT_TIMEOUT_MILLIS: Long = 15_000

    /**
     * 单次请求超时。
     *
     * 设得比较长是因为 `GET /schedule` 会让后端**逐周**向学校拉取整学期数据，
     * 学校 API 慢且有配额，一次完整拉取耗时数十秒属于正常情况（见 API.md §1.1）。
     */
    const val REQUEST_TIMEOUT_MILLIS: Long = 120_000

    // endregion

    /** 拼接完整 URL，自动处理斜杠。 */
    fun url(path: String): String = "${BASE_URL.removeSuffix("/")}/${path.removePrefix("/")}"

    val captchaUrl: String get() = url(PATH_CAPTCHA)
    val loginUrl: String get() = url(PATH_LOGIN)
    val scheduleUrl: String get() = url(PATH_SCHEDULE)
}
