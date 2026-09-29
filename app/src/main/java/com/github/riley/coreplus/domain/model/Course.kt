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
    val name: String,
    val credit: Double,
    val attribute: String,
    val location: String,
    val teacher: String,
    val week: Int,
    val weekday: Weekday,
    val bigSection: Int,
)