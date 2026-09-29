package com.github.riley.coreplus.domain.model

import kotlinx.serialization.Serializable

/**
 * 整学期课表：以周为单位组织，即「多周集合」。
 *
 * 后端 `GET /schedule` 直接返回整学期数据，学期总周数即 [weeks] 的长度。
 * 该类同时是本地 JSON 缓存的顶层结构。
 *
 * @property weeks 该学期的所有周，建议按 [WeeklySchedule.weekNumber] 升序排列
 */
@Serializable
data class Schedule(
    val weeks: List<WeeklySchedule>,
) {
    /** 学期总周数。 */
    val weekCount: Int get() = weeks.size

    /** 按周数查找某一周；不存在（例如未缓存）时返回 null。 */
    fun week(weekNumber: Int): WeeklySchedule? =
        weeks.firstOrNull { it.weekNumber == weekNumber }
}

/**
 * 周课表：某一周的课程列表。
 *
 * 当该周尚未成功拉取时，其 [courses] 为空，
 * 用于实现「未缓存的周显示空白 / 显示上周课程并提示」等场景。
 *
 * @property weekNumber 周数，从 1 开始
 * @property courses 该周的全部课程条目
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
}