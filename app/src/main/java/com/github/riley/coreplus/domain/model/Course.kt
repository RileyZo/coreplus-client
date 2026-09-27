package com.github.riley.coreplus.domain.model

/**
 * 课程数据模型。
 * @property name 课程名称，如 "计算机组成原理"
 * @property credit 课程学分。使用 [Double] 以兼容 0.5 / 1.5 这类半学分课程
 * @property attribute 课程属性，如 "必修"、"选修"、"限选"
 * @property location 上课地点，如 "致远楼605"
 * @property week 周数，如 3 表示第 3 周
 * @property weekday 星期，如 [Weekday.MONDAY]；调用其 [toString] 即可得到 "星期一"
 * @property bigSection 大节序号，如 1 表示第一大节
 */
data class Course(
    val name: String,
    val credit: Double,
    val attribute: String,
    val location: String,
    val week: Int,
    val weekday: Weekday,
    val bigSection: Int,
)
