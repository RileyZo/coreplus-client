package com.github.riley.coreplus.ui.schedule

import com.github.riley.coreplus.domain.model.Schedule
import com.github.riley.coreplus.domain.model.ScheduleLayout
import com.github.riley.coreplus.domain.model.TimeTable
import com.github.riley.coreplus.domain.model.TimeTableSeason
import com.github.riley.coreplus.domain.schedule.CourseBlock
import com.github.riley.coreplus.domain.schedule.ScheduleGrid
import java.time.LocalDate

/**
 * 课表主界面的完整状态。
 *
 * 设计原则：**UI 层不做任何计算**。周次推导、时间计算、连课合并都已在 ViewModel
 * 里算好放进 [grid]，Composable 只负责把状态画出来。这样表格布局可以独立预览与测试。
 *
 * 技术设计文档 §6.1 要求表格与列表**共享同一个 UiState**，因此 [layout] 只是切换渲染分支，
 * 不改变其余任何字段。
 */
data class ScheduleUiState(

    /** 首次数据尚未到达。此时不渲染任何内容，避免闪一下「还没有课表」的空状态。 */
    val initializing: Boolean = true,

    /** 本地缓存的整学期课表；null 表示无缓存。 */
    val schedule: Schedule? = null,

    /** 学期总周数，无缓存时为 0。 */
    val totalWeeks: Int = 0,

    /** 用户当前选中的周次，已裁剪到合法范围。 */
    val selectedWeek: Int = 1,

    /**
     * 实际渲染的是哪一周。
     *
     * 与 [selectedWeek] 不同的情况只有一种：选中周未缓存、回退显示了更早的某周
     * （技术设计文档 §6.3）。null 表示没有任何可显示的数据。
     */
    val displayedWeek: Int? = null,

    /** 非 null 表示正在回退显示该周的课程，UI 需要提示用户。 */
    val fallbackWeek: Int? = null,

    /** 今天是第几周；null 表示无法确定（还没开学、已放假、或未设置学期开始日期）。 */
    val currentWeek: Int? = null,

    /** 生效的学期第一周星期一：用户手动设置值，或自动推断值。 */
    val termStartDate: LocalDate? = null,

    /** [termStartDate] 是否为自动推断（用户尚未手动设置过）。 */
    val termStartIsInferred: Boolean = true,

    /** 当前生效的作息表，[grid] 中的时间由它算出。 */
    val timeTable: TimeTable? = null,

    /** 当前选中的作息表季节。 */
    val season: TimeTableSeason = TimeTableSeason.SUMMER,

    /** 布局方式。当前只实现了 [ScheduleLayout.TABLE]。 */
    val layout: ScheduleLayout = ScheduleLayout.TABLE,

    /** 是否在课表中显示周末两列（周六、周日）。 */
    val showWeekend: Boolean = true,

    /** 已算好的表格数据（含连课合并与实时时间）。 */
    val grid: ScheduleGrid? = null,

    /** 正在向后端拉取课表。 */
    val refreshing: Boolean = false,

    val loginVisible: Boolean = false,
    val timeTableEditorVisible: Boolean = false,
    val termStartPickerVisible: Boolean = false,

    /** 非 null 时显示课程详情对话框。 */
    val detailBlock: CourseBlock? = null,
) {

    /** 是否已有缓存，决定首屏显示引导态还是课表（技术设计文档 §5）。 */
    val hasCache: Boolean get() = schedule != null

    /**
     * 周次选择器的上界。
     *
     * 无缓存时后端还没告诉我们总周数，退回到 [Schedule.FALLBACK_TOTAL_WEEKS]，
     * 保证「下一周」按钮不会直接失效。
     */
    val weekUpperBound: Int get() =
        if (totalWeeks > 0) totalWeeks else Schedule.FALLBACK_TOTAL_WEEKS

    val canGoPrevious: Boolean get() = selectedWeek > 1

    val canGoNext: Boolean get() = selectedWeek < weekUpperBound

    /** 「本周」按钮是否可用。 */
    val canGoToCurrentWeek: Boolean get() = currentWeek != null && currentWeek != selectedWeek

    /** 当前选中周是否已缓存。 */
    val isSelectedWeekCached: Boolean get() = schedule?.week(selectedWeek) != null
}
