package com.github.riley.coreplus.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.riley.coreplus.R

/**
 * 登录窗口（技术设计文档 §6：刷新需重新登录）。
 *
 * 用 [Dialog] 而不是独立页面：内容只有三个输入框，弹窗口比压入一个新页面更轻，
 * 也不需要引入导航库。
 *
 * ## 验证码图片可点击刷新
 *
 * 除了失败时的「点击重试」，正常情况下也允许用户主动点图片换一张——
 * 学校验证码有时扭曲到人也认不出，这时换一张比反复输错更省事。
 *
 * ## 打开时强制换一张验证码
 *
 * `Dialog` 不改变 `LocalViewModelStoreOwner`，[LoginViewModel] 由 Activity 持有、
 * 关闭对话框不会销毁它。若直接复用旧状态，很可能拿到一张已过期的验证码
 * （`captchaId` TTL 只有 300 秒）。因此用 [DisposableEffect] 在打开时强制 [LoginViewModel.refreshCaptcha]，
 * 关闭时 [LoginViewModel.clear] 抹掉所有凭据。
 */
@Composable
fun LoginDialog(
    onDismissRequest: () -> Unit,
    onLoginSucceeded: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(viewModel) {
        viewModel.refreshCaptcha()
        onDispose { viewModel.clear() }
    }

    // 登录成功 → 通知外层关闭对话框并触发课表拉取
    LaunchedEffect(state.loginSucceeded) {
        if (state.loginSucceeded) onLoginSucceeded()
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = state.studentId,
                    onValueChange = viewModel::onStudentIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.login_student_id)) },
                    singleLine = true,
                    enabled = !state.submitting,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    enabled = !state.submitting,
                    visualTransformation = if (state.passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    trailingIcon = {
                        IconButton(onClick = viewModel::togglePasswordVisible) {
                            Icon(
                                painter = painterResource(
                                    if (state.passwordVisible) R.drawable.ic_visibility_off
                                    else R.drawable.ic_visibility,
                                ),
                                contentDescription = stringResource(
                                    if (state.passwordVisible) R.string.login_hide_password
                                    else R.string.login_show_password,
                                ),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.captchaCode,
                        onValueChange = viewModel::onCaptchaCodeChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.login_captcha)) },
                        singleLine = true,
                        enabled = !state.submitting,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.width(12.dp))
                    CaptchaImage(
                        state = state,
                        modifier = Modifier
                            .width(112.dp)
                            .heightIn(min = 56.dp),
                        onRefresh = viewModel::refreshCaptcha,
                    )
                }

                state.errorText?.let { message ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest, enabled = !state.submitting) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = viewModel::submit, enabled = state.canSubmit) {
                        if (state.submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.login_submitting))
                        } else {
                            Text(stringResource(R.string.login_submit))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 验证码图片区域。三种状态共用同一块尺寸，避免加载完成时整个表单跳一下。
 *
 * 整块可点击刷新：失败时是「重试」，成功时是「换一张」。
 */
@Composable
private fun CaptchaImage(
    state: LoginUiState,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = !state.submitting, onClick = onRefresh),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.captchaLoading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )

            state.captchaImage != null -> Image(
                bitmap = state.captchaImage,
                contentDescription = stringResource(R.string.login_captcha_image),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentScale = ContentScale.Fit,
            )

            else -> Text(
                text = stringResource(
                    if (state.captchaFailed) R.string.login_captcha_failed
                    else R.string.login_captcha_loading,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
