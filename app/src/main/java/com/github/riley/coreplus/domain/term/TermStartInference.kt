package com.github.riley.coreplus.domain.term

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * 学期第一周起始日（星期一）的自动推断。
 *
 * 后端只提供当前学期数据、不下发开学日期，因此「今天是第几周」需要一个起点。
 * 用户可以在界面里手动指定（只能选星期一），未指定时使用本对象的推断结果。
 *
 * 推断规则：**取「3 月 1 日」或「9 月 1 日」所在星期的星期一**，
 * 在所有候选中选出不晚于 `today + [TOLERANCE_DAYS]` 的最晚一个。
 *
 * 之所以要留 [TOLERANCE_DAYS] 天容差：开学前一周学生就会打开 App 查看课表，
 * 若严格要求「起始日 ≤ 今天」，8 月底看到的仍是上一学年的春季学期。
 *
 * 举例（今天 2026-09-29）：候选为 2025-08-31、2026-02-23、2026-08-31、2027-03-01，
 * 不晚于 2026-10-06 的最晚一个是 **2026-08-31**（2026-09-01 是星期二，回退到该周星期一）。
 */
object TermStartInference {

    /** 开学前提前识别新学期的容差天数。 */
    const val TOLERANCE_DAYS: Long = 7

    /**
     * 推断 [today] 所处学期的第一周星期一。
     *
     * 纯函数，不读任何持久化状态。
     */
    fun infer(today: LocalDate): LocalDate {
        val candidates = listOf(
            // 上一学年的秋季学期
            mondayOf(LocalDate.of(today.year - 1, 9, 1)),
            // 本学年的春季学期
            mondayOf(LocalDate.of(today.year, 3, 1)),
            // 本学年的秋季学期
            mondayOf(LocalDate.of(today.year, 9, 1)),
            // 下一学年的春季学期（覆盖 12 月底仍在上学期、以及跨年前后的边界）
            mondayOf(LocalDate.of(today.year + 1, 3, 1)),
        )
        val limit = today.plusDays(TOLERANCE_DAYS)
        return candidates.lastOrNull { !it.isAfter(limit) } ?: candidates.first()
    }

    /**
     * 取 [date] 所在星期的星期一。
     *
     * [date] 本身是星期一时原样返回。
     */
    fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** [date] 是否为星期一，用于日期选择器限制可选项。 */
    fun isMonday(date: LocalDate): Boolean = date.dayOfWeek == DayOfWeek.MONDAY
}
