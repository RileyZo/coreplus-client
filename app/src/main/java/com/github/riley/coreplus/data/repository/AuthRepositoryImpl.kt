package com.github.riley.coreplus.data.repository

import android.util.Base64
import android.util.Log
import com.github.riley.coreplus.data.remote.ApiResult
import com.github.riley.coreplus.data.remote.CorePlusApi
import com.github.riley.coreplus.data.remote.TokenStore
import com.github.riley.coreplus.data.remote.dto.CaptchaResponseDto
import com.github.riley.coreplus.data.remote.dto.toDto
import com.github.riley.coreplus.domain.error.AppError
import com.github.riley.coreplus.domain.repository.AuthRepository
import com.github.riley.coreplus.domain.repository.CaptchaResult
import com.github.riley.coreplus.domain.repository.LoginCredentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AuthRepository] 的实现。
 *
 * 只负责两件事：把 Base64 验证码图片解成字节、把登录成功后的 token 交给 [TokenStore]。
 * **凭据不做任何持久化**——学号与密码由调用方（登录对话框的 Compose 状态）持有，
 * 用完即弃，符合技术设计文档 §1「不允许缓存账号密码」。
 */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: CorePlusApi,
    private val tokenStore: TokenStore,
) : AuthRepository {

    override suspend fun captcha(): CaptchaResult =
        when (val result = api.fetchCaptcha()) {
            is ApiResult.Error -> CaptchaResult.Failure(result.error)
            is ApiResult.Success -> decodeImage(result.data)
        }

    /**
     * 把 Base64 图片解成原始字节，交给 UI 层用 `BitmapFactory` 解码。
     *
     * 这里**不做**图片格式判断：学校可能给 PNG 也可能给 JPEG，
     * `BitmapFactory.decodeByteArray` 会自动嗅探，客户端不必关心 `imageMediaType`。
     */
    private fun decodeImage(dto: CaptchaResponseDto): CaptchaResult {
        val bytes = runCatching { Base64.decode(dto.imageBase64, Base64.DEFAULT) }
            .onFailure { Log.w(TAG, "验证码图片 Base64 解码失败", it) }
            .getOrNull()

        return if (bytes == null || bytes.isEmpty()) {
            CaptchaResult.Failure(AppError.MalformedResponse)
        } else {
            CaptchaResult.Success(
                captchaId = dto.captchaId,
                imageBytes = bytes,
                expiresInSeconds = dto.expiresIn,
            )
        }
    }

    /**
     * 提交登录。
     *
     * 开头先 [TokenStore.clear]：技术设计文档要求「刷新需重新登录」，
     * 即便上一个 token 理论上还没过期也一律丢弃，避免出现「用旧 token 拉数据」的隐蔽路径。
     */
    override suspend fun login(credentials: LoginCredentials): AppError? {
        tokenStore.clear()

        return when (val result = api.login(credentials.toDto())) {
            is ApiResult.Error -> result.error
            is ApiResult.Success -> {
                tokenStore.update(result.data.token)
                if (!tokenStore.hasToken) {
                    // 后端返回了 200 但 token 为空：当作后端异常，避免后续 refresh 拿到 null token
                    Log.w(TAG, "登录响应中的 token 为空")
                    AppError.MalformedResponse
                } else {
                    null
                }
            }
        }
    }

    override fun logout() {
        tokenStore.clear()
    }

    override fun hasToken(): Boolean = tokenStore.hasToken

    private companion object {
        const val TAG = "AuthRepository"
    }
}
