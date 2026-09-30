package com.github.riley.coreplus.domain.model

import kotlinx.serialization.Serializable

/**
 * 课程数据模型。
 *
 * 一条记录对应「一个大节」，即数据层显式以「周数 + 星期几 + 大节」定位课程；
 * 一条课程记录所占的小节（如 `1-1`、`1-2`）由当前 [TimeTable] 中大节的划分推导，不在此存储。
 *
 * 四小节连课在数据层拆成两个独立大节，仅在 UI 层依据
 * [name]、[location]、[teacher] 三个字段判断是否合并显示。
 *
 * @property id 学期内唯一且稳定的标识，用于删除课程与 Compose 列表 key。
 *   优先取自后端下发值；后端未下发时由 [syntheticId] 合成（合成值在课程改名后会变化）
 * @property name 课程名称，如 "计算机组成原理"
 * @property credit 课程学分。使用 [Double] 以兼容 0.5 / 1.5 这类半学分课程
 * @property attribute 课程属性，如 "必修"、"选修"、"限选"
 * @property location 上课地点，如 "致远楼605"
 * @property teacher 任课教师。UI 展示课程块、以及「同一天连续大节是否合并」的判断均依赖该字段
 * @property week 周数，如 3 表示第 3 周
 * @property weekday 星期，如 [Weekday.MONDAY]；调用其 [toString] 即可得到 "星期一"
 * @property bigSection 大节序号，如 1 表示第一大节
 */
@Serializable
data class Course(
    val id: String,
    val name: String,
    val credit: Double,
    val attribute: String,
    val location: String,
    val teacher: String,
    val week: Int,
    val weekday: Weekday,
    val bigSection: Int,
) {
    /**
     * 是否与 [other] 属于「同一门课」。
     *
     * 依据技术设计文档 §2.4：只看课程名称、上课教室、教师三个字段。
     * UI 层用它判断同一天的相邻大节是否应该视觉合并为一个块。
     */
    fun isSameCourseAs(other: Course): Boolean =
        name == other.name && location == other.location && teacher == other.teacher

    companion object {
        /**
         * 后端未下发 `id` 时合成一个确定性标识。
         *
         * 同一格子上的同一门课每次合成结果相同，因此刷新后不会让 Compose 把它当成新课程。
         * 使用无符号十六进制避免负数 hashCode 产生连续两个连字符。
         */
        fun syntheticId(week: Int, weekday: Weekday, bigSection: Int, name: String): String =
            "w$week-d${weekday.value}-s$bigSection-${name.hashCode().toUInt().toString(16)}"
    }
}