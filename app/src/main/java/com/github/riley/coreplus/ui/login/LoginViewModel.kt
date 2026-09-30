package com.github.riley.coreplus.ui.login

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.riley.coreplus.R
import com.github.riley.coreplus.di.DefaultDispatcher
import com.github.riley.coreplus.domain.error.requiresNewCaptcha
import com.github.riley.coreplus.domain.repository.AuthRepository
import com.github.riley.coreplus.domain.repository.CaptchaResult
import com.github.riley.coreplus.domain.repository.LoginCredentials
import com.github.riley.coreplus.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 登录窗口的 ViewModel：验证码获取 + 登录提交。
 *
 * ## captchaId 是一次性的
 *
 * `docs/API.md` §4.2 约定：无论登录成功还是失败，一次 `POST /auth/login` 之后
 * `captchaId` 立即作废（学校侧的验证码校验一次就失效）。因此
 * [submit] 在**任何失败分支**都会调用 [refreshCaptcha]——具体由
 * [requiresNewCaptcha] 判定，将来新增错误类型时编译器不会漏掉这里，
 * 但需要人工确认它是否该触发换图。
 *
 * ## 凭据不落盘
 *
 * 学号与密码只存在于 [state]；[clear] 会在对话框关闭时把整个状态重置为初始值。
 * token 由 `AuthRepository` 写进内存单例，同样不落盘（技术设计文档 §1）。
 *
 * ## 生命周期
 *
 * `Dialog` 不会改变 `LocalViewModelStoreOwner`，所以本 ViewModel 实际由 Activity 持有，
 * 关闭对话框并不会销毁它。因此**不依赖 `init` 拉验证码**：
 * 对话框每次打开时通过 `DisposableEffect` 显式调用 [refreshCaptcha]，
 * 关闭时调用 [clear]，避免复用到一张早已过期的验证码。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val state = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = state.asStateFlow()

    /** 当前验证码句柄。属于数据而非展示内容，因此不放进 [LoginUiState]。 */
    private var captchaId: String? = null

    /** 拉一张新验证码。会清空已输入的验证码文本，因为旧答案必然作废。 */
    fun refreshCaptcha() {
        viewModelScope.launch {
            captchaId = null
            state.update { it.copy(captchaLoading = true, captchaFailed = false, captchaCode = "") }

            when (val result = authRepository.captcha()) {
                is CaptchaResult.Failure -> state.update {
                    it.copy(
                        captchaLoading = false,
                        captchaFailed = true,
                        captchaImage = null,
                        errorText = result.error.toUserMessage(context),
                    )
                }

                is CaptchaResult.Success -> {
                    val image = decode(result.imageBytes)
                    if (image == null) {
                        state.update {
                            it.copy(
                                captchaLoading = false,
                                captchaFailed = true,
                                errorText = context.getString(R.string.error_malformed_response),
                            )
                        }
                    } else {
                        captchaId = result.captchaId
                        state.update {
                            it.copy(captchaLoading = false, captchaFailed = false, captchaImage = image, errorText = null)
                        }
                    }
                }
            }
        }
    }

    /** 图片解码是 CPU 活，放到后台线程；验证码虽小，也不该在主线程做。 */
    private suspend fun decode(bytes: ByteArray): ImageBitmap? = withContext(defaultDispatcher) {
        val bitmap = runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            .onFailure { Log.w(TAG, "验证码图片解码失败", it) }
            .getOrNull()
        bitmap?.asImageBitmap()
    }

    fun onStudentIdChange(value: String) {
        state.update { it.copy(studentId = value, errorText = null) }
    }

    fun onPasswordChange(value: String) {
        state.update { it.copy(password = value, errorText = null) }
    }

    fun onCaptchaCodeChange(value: String) {
        state.update { it.copy(captchaCode = value, errorText = null) }
    }

    fun togglePasswordVisible() {
        state.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    /** 提交登录。成功后置 [LoginUiState.loginSucceeded]，由 UI 层关闭对话框并触发课表拉取。 */
    fun submit() {
        val current = state.value
        if (current.submitting) return

        // 本地先校验，不打无谓的网络请求——学校接口有严格配额
        val missingRes = when {
            current.studentId.isBlank() -> R.string.login_student_id_required
            current.password.isBlank() -> R.string.login_password_required
            current.captchaCode.isBlank() -> R.string.login_captcha_required
            else -> null
        }
        if (missingRes != null) {
            state.update { it.copy(errorText = context.getString(missingRes)) }
            return
        }

        val id = captchaId
        if (id == null) {
            // 还没有有效验证码（首次加载失败或被消费掉了），先换一张
            refreshCaptcha()
            return
        }

        viewModelScope.launch {
            state.update { it.copy(submitting = true, errorText = null) }

            val error = authRepository.login(
                LoginCredentials(
                    studentId = current.studentId,
                    password = current.password,
                    captchaId = id,
                    captchaCode = current.captchaCode,
                ),
            )

            if (error == null) {
                // 立刻抹掉密码；对话框随后关闭并调用 clear() 做二次清理
                state.update { it.copy(submitting = false, loginSucceeded = true, password = "", captchaCode = "") }
                return@launch
            }

            Log.w(TAG, "登录失败: $error")
            captchaId = null   // 一次性句柄，用过即废
            state.update {
                it.copy(submitting = false, errorText = error.toUserMessage(context))
            }
            // 验证码或密码错误都会让学校侧验证码作废，必须换图；
            // 网络类错误（连不上、超时）则不必浪费一次学校请求
            if (error.requiresNewCaptcha) refreshCaptcha()
        }
    }

    /** 对话框关闭时调用：抹掉学号、密码、验证码与图片。 */
    fun clear() {
        captchaId = null
        state.value = LoginUiState()
    }

    private companion object {
        const val TAG = "LoginViewModel"
    }
}
