package com.github.riley.coreplus.di

import android.util.Log
import com.github.riley.coreplus.BuildConfig
import com.github.riley.coreplus.data.remote.ApiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * 网络层依赖：Ktor `HttpClient`（OkHttp 引擎，技术设计文档 §8 的选型）。
 *
 * `CorePlusApi` 与各数据源都带 `@Inject constructor` + `@Singleton`，
 * Hilt 能自行构造，因此本模块只需提供 `HttpClient` 这一个手工装配的对象。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 登录路径的末段（`auth/login` → `login`）。
     *
     * 用于把登录请求从日志中整个过滤掉：它的请求体含明文密码。
     * 取末段而不是整串匹配，是因为日志过滤器拿到的是已拆解的 URL 路径段。
     */
    private val LOGIN_PATH_SEGMENT: String = ApiConfig.PATH_LOGIN.substringAfterLast('/')

    /**
     * 应用唯一的 [HttpClient] 实例。
     *
     * 几处刻意配置：
     * - **`expectSuccess = false`**：让 [com.github.riley.coreplus.data.remote.CorePlusApi]
     *   自己检查状态码。Ktor 的自动校验会在读 body 之前抛异常，
     *   那样就拿不到后端的错误信封 `{code, message}`，客户端也就无法判断
     *   「是否该自动换一张验证码」。
     * - **禁用 OkHttp 自动重试**：学校 API 有严格的请求配额，
     *   连接失败时的隐式重试会放大请求数，可能直接触发学校侧封禁。
     * - **日志仅 debug 构建安装**，且做两重脱敏（见下）。
     */
    @Provides
    @Singleton
    fun provideHttpClient(@ApplicationJson json: Json): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(json, ContentType.Application.Json)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = ApiConfig.CONNECT_TIMEOUT_MILLIS
            requestTimeoutMillis = ApiConfig.REQUEST_TIMEOUT_MILLIS
            socketTimeoutMillis = ApiConfig.REQUEST_TIMEOUT_MILLIS
        }

        if (BuildConfig.DEBUG) {
            install(Logging) {
                level = LogLevel.BODY
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("CorePlusHttp", message)
                    }
                }
                // 脱敏之一：token 只以 "***" 出现在日志里
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
                // 脱敏之二：登录请求体含明文密码，整个请求不记日志（见 API.md §6）
                filter { request -> request.url.pathSegments.lastOrNull() != LOGIN_PATH_SEGMENT }
            }
        }

        engine {
            // 通过 OkHttpClient.Builder 关闭连接失败自动重试
            config {
                retryOnConnectionFailure(false)
            }
        }
    }
}
