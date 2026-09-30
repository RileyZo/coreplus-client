package com.github.riley.coreplus.ui.login

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 登录窗口的状态。
 *
 * **不含学号与密码以外的任何凭据**：`captchaId` 刻意留在 [LoginViewModel] 的私有字段里，
 * 因为它是数据而非展示内容，放进状态只会在 Compose 里无意义地参与相等性比较。
 *
 * @property captchaLoading 正在向后端请求验证码图片
 * @property captchaFailed 验证码获取或解码失败，UI 显示「点击重试」
 * @property captchaImage 已解码的验证码图片
 * @property errorText 已解析好的错误文案，直接展示
 * @property loginSucceeded 登录成功。UI 观察它来关闭对话框并触发课表拉取
 */
data class LoginUiState(
    val captchaLoading: Boolean = false,
    val captchaFailed: Boolean = false,
    val captchaImage: ImageBitmap? = null,
    val studentId: String = "",
    val password: String = "",
    val captchaCode: String = "",
    val passwordVisible: Boolean = false,
    val submitting: Boolean = false,
    val errorText: String? = null,
    val loginSucceeded: Boolean = false,
) {
    /** 「登录」按钮是否可点。提交中禁用，避免重复请求打到有配额的学校接口上。 */
    val canSubmit: Boolean get() = !submitting && !captchaLoading && studentId.isNotBlank() &&
        password.isNotBlank() && captchaCode.isNotBlank()
}
