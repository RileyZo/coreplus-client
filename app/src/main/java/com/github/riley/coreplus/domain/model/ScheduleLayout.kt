package com.github.riley.coreplus.domain.model

/**
 * 课表的展示布局。
 *
 * 技术设计文档 §6.1 要求预留的抽象：UI 层根据本枚举选择不同的 Composable 实现，
 * 但共享同一个 `ScheduleUiState`。当前只实现了 [TABLE]，[LIST] 走占位界面。
 *
 * 用户的选择会被持久化，重启后恢复。
 *
 * @property label 中文名称，用于菜单项文案
 */
enum class ScheduleLayout(val label: String) {
    /** 二维网格：星期几 × 节次。 */
    TABLE("表格"),

    /** 按时间顺序的列表。尚未实现。 */
    LIST("列表");

    /** 返回中文名称，例如 `ScheduleLayout.TABLE.toString()` 得到 "表格"。 */
    override fun toString(): String = label

    companion object {
        /** 解析持久化的枚举名；无法识别时回退到 [TABLE]。 */
        fun fromName(name: String?): ScheduleLayout =
            entries.firstOrNull { it.name == name } ?: TABLE
    }
}
