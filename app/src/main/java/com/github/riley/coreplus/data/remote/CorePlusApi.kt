package com.github.riley.coreplus.data.remote

import android.util.Log
import com.github.riley.coreplus.data.remote.dto.ApiErrorDto
import com.github.riley.coreplus.data.remote.dto.CaptchaResponseDto
import com.github.riley.coreplus.data.remote.dto.LoginRequestDto
import com.github.riley.coreplus.data.remote.dto.LoginResponseDto
import com.github.riley.coreplus.data.remote.dto.ScheduleResponseDto
import com.github.riley.coreplus.di.ApplicationJson
import com.github.riley.coreplus.domain.error.AppError
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * 后端 HTTP 接口服务，见 `docs/API.md` §4。
 *
 * 三个约定：
 *
 * 1. **不向外抛业务异常**。所有方法返回 [ApiResult]，Ktor 异常与 HTTP 错误码在 [execute] 内
 *    统一翻译成 [AppError]，Repository 层因此不需要写 try/catch。
 * 2. **不用 `expectSuccess = true`**。Ktor 的自动校验会在读 body 之前就抛异常，
 *    导致拿不到后端的错误信封（`{code, message}`）。这里手动检查状态码，
 *    保证错误响应体一定能被读到并解析出 `code`——客户端要靠它决定「是否自动换验证码」。
 * 3. **协程取消必须原样抛出**，不能被 catch 成网络错误，否则界面会卡在加载态。
 *
 * 响应体用 [Json] 手动解码而非 `response.body()`，一是为了与本地缓存共用同一套 `Json` 配置
 * （`ignoreUnknownKeys` / `coerceInputValues` 等），行为完全一致、便于排查；
 * 二是因为空响应体会让 `body()` 抛 `NoContentTransformationException`，
 * 而手动解码能把它统一归入 [AppError.MalformedResponse]。
 */
@Singleton
class CorePlusApi @Inject constructor(
    private val client: HttpClient,
    @ApplicationJson private val json: Json,
) {

    /** `GET /api/auth/captcha`，API.md §4.1。 */
    suspend fun fetchCaptcha(): ApiResult<CaptchaResponseDto> =
        execute(CaptchaResponseDto.serializer()) { httpClient -> httpClient.get(ApiConfig.captchaUrl) }

    /**
     * `POST /api/auth/login`，API.md §4.2。
     *
     * 请求体含明文密码，日志层已把本请求整体过滤掉（见 `di/NetworkModule`）。
     */
    suspend fun login(request: LoginRequestDto): ApiResult<LoginResponseDto> =
        execute(LoginResponseDto.serializer()) { httpClient ->
            httpClient.post(ApiConfig.loginUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /** `GET /api/schedule`，API.md §4.3。需要 `Authorization: Bearer <token>`。 */
    suspend fun fetchSchedule(token: String): ApiResult<ScheduleResponseDto> =
        execute(ScheduleResponseDto.serializer()) { httpClient ->
            httpClient.get(ApiConfig.scheduleUrl) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        }

    /**
     * 执行一次请求并把结果归一化为 [ApiResult]。
     *
     * 响应体只读取一次（`bodyAsText`），成功与失败分支共用同一份文本，
     * 避免 Ktor 的响应 channel 被重复消费。
     */
    private suspend fun <T> execute(
        serializer: KSerializer<T>,
        block: suspend (HttpClient) -> HttpResponse,
    ): ApiResult<T> {
        val response = try {
            block(client)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return ApiResult.Error(transportError(e))
        }

        val text = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "读取响应体失败: ${response.status}", e)
            return ApiResult.Error(AppError.Unreachable)
        }

        if (!response.status.isSuccess()) {
            return ApiResult.Error(
                httpError(response.status.value, text, response.headers[HttpHeaders.RetryAfter]),
            )
        }

        return try {
            ApiResult.Success(json.decodeFromString(serializer, text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "响应体无法解析为 ${serializer.descriptor.serialName}", e)
            ApiResult.Error(AppError.MalformedResponse)
        }
    }

    /**
     * 把非 2xx 响应翻译成 [AppError]。
     *
     * 优先按错误信封的 `code` 分派（契约见 API.md §3.2）；
     * `code` 缺失或不在契约内时退化为按 HTTP 状态码分派，
     * 这样即便后端还没实现错误信封，客户端也能给出大致正确的行为。
     */
    private fun httpError(status: Int, body: String, retryAfterHeader: String?): AppError {
        val envelope = body.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(ApiErrorDto.serializer(), it) }.getOrNull() }
        val code = envelope?.code
        val message = envelope?.message?.takeIf { it.isNotBlank() }
        val retryAfter = retryAfterHeader?.trim()?.toIntOrNull()

        return when (code) {
            CODE_INVALID_REQUEST -> AppError.BadRequest(message)
            CODE_CAPTCHA_INVALID, CODE_CAPTCHA_EXPIRED -> AppError.CaptchaRejected(message)
            CODE_CREDENTIALS_INVALID -> AppError.CredentialsRejected(message)
            CODE_TOKEN_EXPIRED -> AppError.LoginRequired(AppError.LoginRequired.Reason.TOKEN_EXPIRED)
            CODE_TOKEN_INVALID -> AppError.LoginRequired(AppError.LoginRequired.Reason.TOKEN_INVALID)
            CODE_UPSTREAM_SESSION_LOST ->
                AppError.LoginRequired(AppError.LoginRequired.Reason.UPSTREAM_SESSION_LOST)

            CODE_RATE_LIMITED -> AppError.RateLimited(retryAfter)
            CODE_UPSTREAM_UNAVAILABLE -> AppError.UpstreamUnavailable(message)
            else -> {
                if (code != null) Log.w(TAG, "后端返回了契约外的错误码: $code")
                errorByStatus(status, code, message, retryAfter)
            }
        }
    }

    /** 状态码兜底分派，用于后端未按契约给出 `code` 的情况（含网关直接返回的错误）。 */
    private fun errorByStatus(status: Int, code: String?, message: String?, retryAfter: Int?): AppError =
        when (status) {
            400 -> AppError.BadRequest(message)
            401 -> AppError.LoginRequired(AppError.LoginRequired.Reason.TOKEN_INVALID)
            429 -> AppError.RateLimited(retryAfter)
            502, 504 -> AppError.UpstreamUnavailable(message)
            else -> AppError.Server(status, code, message)
        }

    /**
     * 把传输层异常翻译成 [AppError]。
     *
     * 分支顺序**不可调整**：
     * - `HttpRequestTimeoutException` 继承自 [IOException]，必须排在 [IOException] 之前，
     *   否则超时会被误判为「连不上」
     * - `TimeoutCancellationException` 继承自 [CancellationException]，必须排在其之前，
     *   否则超时会被当成协程取消重新抛出
     */
    private fun transportError(e: Throwable): AppError = when (e) {
        is HttpRequestTimeoutException -> AppError.Timeout
        is SocketTimeoutException -> AppError.Timeout
        is TimeoutCancellationException -> AppError.Timeout
        is CancellationException -> throw e
        is UnknownHostException -> AppError.Unreachable
        is ConnectException -> AppError.Unreachable
        is NoRouteToHostException -> AppError.Unreachable
        is IOException -> AppError.Unreachable
        is SerializationException -> AppError.MalformedResponse
        else -> AppError.Server(null, null, e.message)
    }

    private companion object {
        const val TAG = "CorePlusApi"

        // 后端错误码，取值必须与 docs/API.md §3.2 的表完全一致
        const val CODE_INVALID_REQUEST = "INVALID_REQUEST"
        const val CODE_CAPTCHA_INVALID = "CAPTCHA_INVALID"
        const val CODE_CAPTCHA_EXPIRED = "CAPTCHA_EXPIRED"
        const val CODE_CREDENTIALS_INVALID = "CREDENTIALS_INVALID"
        const val CODE_TOKEN_EXPIRED = "TOKEN_EXPIRED"
        const val CODE_TOKEN_INVALID = "TOKEN_INVALID"
        const val CODE_UPSTREAM_SESSION_LOST = "UPSTREAM_SESSION_LOST"
        const val CODE_RATE_LIMITED = "RATE_LIMITED"
        const val CODE_UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE"
    }
}
