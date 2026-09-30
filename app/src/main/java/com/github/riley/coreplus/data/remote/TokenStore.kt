package com.github.riley.coreplus.data.remote

import javax.inject.Inject
import javax.inject.Singleton

/**
 * token 的内存保管处。
 *
 * 技术设计文档 §1：**token 有效期约 1 小时，不缓存**。因此这里刻意
 * **不使用** DataStore、SharedPreferences 或文件——只有一个 `@Volatile` 字段。
 * 进程被系统回收后 token 自然消失，下次启动必然走重新登录，正是设计要求的行为。
 *
 * token 的本质是后端所持「已认证学校会话」的句柄（见 `docs/API.md` §2），
 * 客户端不解析它的内容，只负责原样带上 `Authorization: Bearer`。
 *
 * 做成 `@Singleton` 是为了让 `AuthRepository`（写入）与 `ScheduleRepository`（读取）
 * 共享同一份状态，而不必让 token 穿过 UI 层。
 */
@Singleton
class TokenStore @Inject constructor() {

    /** 当前 token；null 表示尚未登录或已失效。 */
    @Volatile
    var token: String? = null
        private set

    /** 是否持有可用 token。 */
    val hasToken: Boolean get() = !token.isNullOrBlank()

    /** 写入新 token，替换旧值。 */
    fun update(newToken: String?) {
        token = newToken?.takeIf { it.isNotBlank() }
    }

    /**
     * 清空 token。
     *
     * 调用时机：刷新流程开始时、收到任何 401 时、用户主动登出时。
     */
    fun clear() {
        token = null
    }

    /** 刻意不重写 [toString]，避免 token 被意外打进日志。 */
    override fun toString(): String = "TokenStore(token=${if (hasToken) "***" else "null"})"
}
