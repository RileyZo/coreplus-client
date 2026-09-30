package com.github.riley.coreplus.ui.common

/**
 * 一条一次性用户提示，由 ViewModel 通过 Channel 发出、UI 用 Snackbar 呈现。
 *
 * 刻意**不放进** `ScheduleUiState`：提示是事件而非状态，
 * 放进状态会导致旋转屏幕后重复弹出，还得额外维护「已消费」标记。
 *
 * @property text 已解析好的文案。ViewModel 持有 Application Context，
 *   因此字符串拼接（如「已更新 3 周课表」）在这里完成，UI 层直接显示
 * @property actionLabel 非 null 时 Snackbar 会带一个动作按钮
 * @property onAction 点击动作按钮时执行；为 null 时按钮不可用
 */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
) {
    /** 是否带动作按钮。 */
    val hasAction: Boolean get() = actionLabel != null && onAction != null
}
