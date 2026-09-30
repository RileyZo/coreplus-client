package com.github.riley.coreplus.domain.model

import kotlinx.serialization.Serializable

/**
 * 整学期课表：以周为单位组织，即「多周集合」。
 *
 * 后端 `GET /api/schedule` 直接返回整学期数据，学期总周数由 [totalWeeks] 给出，
 * 而 [weeks] 只包含**已成功拉取到的周**（是 `1..totalWeeks` 的子集）。
 * 该类同时是本地 JSON 缓存的顶层结构。
 *
 * 「[weeks] 只是子集」这一点是部分成功语义的基础：后端向学校逐周拉取时失败的周会被省略，
 * 客户端在 [merge] 时保留这些周的旧缓存。因此
 * **[WeeklySchedule.courses] 为空表示「这周确实没课」，而 [week] 返回 null 表示「这周未缓存」**，
 * 两者含义不同，UI 据此决定是否回退显示上一周的课程。
 *
 * @property totalWeeks 学期总周数，来自后端；作为周次选择器的上界
 * @property weeks 已缓存的周，按 [WeeklySchedule.weekNumber] 升序排列
 */
@Serializable
data class Schedule(
    val totalWeeks: Int,
    val weeks: List<WeeklySchedule> = emptyList(),
) {
    /** 已缓存的周次集合。 */
    val knownWeekNumbers: Set<Int> get() = weeks.mapTo(mutableSetOf()) { it.weekNumber }

    /**
     * 本次数据中缺失的周次，即 `(1..totalWeeks) - knownWeekNumbers`。
     *
     * 用于实现技术设计文档 §3 的「部分成功」提示。缓存刚合并完时，
     * 这里列出的就是历史上从未成功拉取过的周。
     */
    val missingWeekNumbers: List<Int>
        get() = (1..totalWeeks).filterNot { it in knownWeekNumbers }

    /** 按周数查找某一周；未缓存时返回 null。 */
    fun week(weekNumber: Int): WeeklySchedule? =
        weeks.firstOrNull { it.weekNumber == weekNumber }

    /**
     * 把 [other]（一次刷新的结果）合并进当前课表。
     *
     * 合并规则（技术设计文档 §3「部分成功」）：
     * - [other] 中出现的周 → 覆盖本地同周数据
     * - [other] 中未出现的周 → 保留本地缓存
     * - 周次超出 [other].totalWeeks 的本地数据 → 丢弃（新学期周数变少，或上学期的残留）
     * - [totalWeeks] 一律取 [other] 的值
     *
     * [other].weeks 为空时（一次都没拉到），本地缓存被完整保留，只更新总周数。
     */
    fun merge(other: Schedule): Schedule {
        if (other.weeks.isEmpty()) return copy(totalWeeks = other.totalWeeks)

        val byNumber = weeks.associateBy { it.weekNumber }.toMutableMap()
        other.weeks.forEach { byNumber[it.weekNumber] = it }
        byNumber.keys.removeAll { it > other.totalWeeks }

        return Schedule(
            totalWeeks = other.totalWeeks,
            weeks = byNumber.values.sortedBy { it.weekNumber },
        )
    }

    companion object {
        /** 后端未给出总周数时，周次选择器使用的兜底上界。 */
        const val FALLBACK_TOTAL_WEEKS = 25

        /** 空课表，用于「尚无缓存」时占位。 */
        val Empty = Schedule(totalWeeks = 0)
    }
}

/**
 * 周课表：某一周的课程列表。
 *
 * 注意本类的一个实例存在于 [Schedule.weeks] 中，就代表该周**已被成功拉取过**；
 * 「该周未缓存」由 [Schedule.week] 返回 null 表达，不会用一个空的 [WeeklySchedule] 表示。
 *
 * @property weekNumber 周数，从 1 开始
 * @property courses 该周的全部课程条目，确实无课时为空列表
 */
@Serializable
data class WeeklySchedule(
    val weekNumber: Int,
    val courses: List<Course> = emptyList(),
) {
    /** 该周是否没有课程。 */
    val isEmpty: Boolean get() = courses.isEmpty()

    /** 取某一天的课程，便于表格布局按列渲染。 */
    fun coursesOn(weekday: Weekday): List<Course> =
        courses.filter { it.weekday == weekday }

    /** 取某天大节下的课程，命中不到时返回 null。 */
    fun courseAt(weekday: Weekday, bigSection: Int): Course? =
        courses.firstOrNull { it.weekday == weekday && it.bigSection == bigSection }

    /** 删除指定课程（按 [Course.id] 匹配），返回新的周课表。 */
    fun withoutCourse(course: Course): WeeklySchedule =
        copy(courses = courses.filterNot { it.id == course.id })
}
