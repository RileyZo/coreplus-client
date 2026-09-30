package com.github.riley.coreplus.data.remote.dto

import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.model.Schedule
import com.github.riley.coreplus.domain.model.Weekday
import com.github.riley.coreplus.domain.model.WeeklySchedule
import kotlinx.serialization.Serializable

/**
 * `GET /api/schedule` 的响应，见 `docs/API.md` §4.3。
 *
 * 两个刻意的设计，后端对接时务必注意：
 *
 * 1. **课程条目内没有 `week` 字段**——所属周由外层 [WeekDto.weekNumber] 决定，
 *    映射时由 [toSchedule] 自动填入 [Course.week]，避免同一信息重复两次。
 * 2. **[weeks] 是 `1..totalWeeks` 的子集**——拉取失败的周直接省略，
 *    不要用「`courses` 为空」占位。这是「部分成功」语义的基础：
 *    客户端会保留省略周的本地缓存。详见 [toSchedule] 与 API.md §4.3 规则 3。
 */
@Serializable
data class ScheduleResponseDto(
    /** 学期总周数，作为周次选择器上界并用于判断部分成功。 */
    val totalWeeks: Int = 0,
    /** 成功拉取到的周，可以是空数组。 */
    val weeks: List<WeekDto> = emptyList(),
)

@Serializable
data class WeekDto(
    /** 周次，取值 `1..totalWeeks`，同一响应内不重复。 */
    val weekNumber: Int,
    /** 该周全部课程；确实无课时为空数组。 */
    val courses: List<CourseDto> = emptyList(),
)

/**
 * 一条课程记录，对应「一个大节」。
 *
 * 四小节连课由后端**拆成两条** `bigSection` 相邻的记录下发，且 [name]、[location]、[teacher]
 * 三者完全一致，客户端才能在 UI 层合并成一个跨行块（API.md §4.3 规则 1）。
 *
 * 所有非关键字段都给了默认值：后端某个字段缺失时不至于让整份课表解析失败。
 */
@Serializable
data class CourseDto(
    /** 学期内唯一稳定标识。缺失时客户端会合成，但强烈建议下发。 */
    val id: String? = null,
    val name: String = "",
    val credit: Double = 0.0,
    val attribute: String = "",
    val location: String = "",
    val teacher: String = "",
    /** ISO-8601 星期：1=星期一 … 7=星期日。 */
    val weekday: Int = 1,
    /** 大节序号，从 1 开始。 */
    val bigSection: Int = 1,
)

// region DTO → 领域模型映射

/**
 * 把响应映射为领域模型 [Schedule]。
 *
 * 做了三重防御，因为数据源头是学校教务系统、经后端转发，任何一环都可能出脏数据：
 * - 丢弃 `weekNumber < 1` 的周
 * - 同一周号重复出现时只取第一条
 * - 丢弃无法映射的课程（见 [toCourse]）
 *
 * 后端未按契约给出 `totalWeeks` 时，退化为「已知周数」，保证周次选择器至少能覆盖已有数据。
 */
fun ScheduleResponseDto.toSchedule(): Schedule {
    val mapped = weeks.asSequence()
        .filter { it.weekNumber >= 1 }
        .distinctBy { it.weekNumber }
        .map { it.toWeeklySchedule() }
        .sortedBy { it.weekNumber }
        .toList()

    val total = if (totalWeeks > 0) totalWeeks else mapped.size
    return Schedule(totalWeeks = total, weeks = mapped)
}

private fun WeekDto.toWeeklySchedule(): WeeklySchedule = WeeklySchedule(
    weekNumber = weekNumber,
    courses = courses.asSequence()
        .mapNotNull { it.toCourse(weekNumber) }
        // 同一格子出现多条时只保留第一条（API.md §4.3 规则 2）
        .distinctBy { it.weekday to it.bigSection }
        .toList(),
)

/**
 * 映射单条课程；数据非法时返回 null 表示**丢弃这一条**，而不是让整份课表失败。
 *
 * 丢弃条件：星期序号不在 1..7、大节序号小于 1、课程名为空。
 *
 * @param week 所属周次，由外层 [WeekDto.weekNumber] 提供，用于填充 [Course.week] 与合成 id
 */
private fun CourseDto.toCourse(week: Int): Course? {
    val day = Weekday.fromValue(weekday) ?: return null
    if (bigSection < 1) return null
    if (name.isBlank()) return null

    return Course(
        id = id?.takeIf { it.isNotBlank() } ?: Course.syntheticId(week, day, bigSection, name),
        name = name,
        credit = credit,
        attribute = attribute,
        location = location,
        teacher = teacher,
        week = week,
        weekday = day,
        bigSection = bigSection,
    )
}

// endregion
