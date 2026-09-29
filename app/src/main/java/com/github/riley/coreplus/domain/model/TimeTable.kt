package com.github.riley.coreplus.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalTime

/**
 * 作息表的季节类型。作息表结构固定，始终只有夏季、冬季两套。
 *
 * @property label 中文名称，如 "夏季"
 */
enum class TimeTableSeason(val label: String) {
    SUMMER("夏季"),
    WINTER("冬季");

    /** 返回中文名称，例如 `TimeTableSeason.SUMMER.toString()` 得到 "夏季"。 */
    override fun toString(): String = label
}

/**
 * 作息时间表。
 *
 * 结构固定：由若干 [BigSection]（大节）组成，每个大节再划分出若干 [Section]（小节）。
 * 用户仅可修改小节的起止时间，大节的起止时间由所属小节实时推导。
 *
 * @property season 该作息表对应的季节
 * @property bigSections 所有大节，建议按 [BigSection.index] 升序排列
 */
@Serializable
data class TimeTable(
    val season: TimeTableSeason,
    val bigSections: List<BigSection>,
) {
    /** 所有小节，按大节顺序展开。 */
    val sections: List<Section> get() = bigSections.flatMap { it.sections }

    /** 按大节序号查找大节，不存在时返回 null。 */
    fun bigSection(index: Int): BigSection? =
        bigSections.firstOrNull { it.index == index }
}

/**
 * 大节。显式定义，包含其小节划分。
 *
 * 大节的起止时间不是独立字段，而是「首小节开始时间 → 末小节结束时间」。
 *
 * @property index 大节序号，从 1 开始
 * @property sections 该大节包含的小节，按先后顺序排列
 */
@Serializable
data class BigSection(
    val index: Int,
    val sections: List<Section>,
) {
    init {
        require(sections.isNotEmpty()) { "大节必须至少包含一个小节" }
    }

    /** 大节开始时间 = 首小节开始时间。 */
    val startTime: LocalTime get() = sections.first().startTime

    /** 大节结束时间 = 末小节结束时间。 */
    val endTime: LocalTime get() = sections.last().endTime

    /** 小节编号，形如 "1-1"、"1-2"（大节序号-小节序号）。 */
    fun sectionNumber(section: Section): String = "$index-${section.index}"
}

/**
 * 小节。
 *
 * @property index 小节序号，在同一大节内从 1 开始
 * @property startTime 开始时间
 * @property endTime 结束时间
 */
@Serializable
data class Section(
    val index: Int,
    @Serializable(with = LocalTimeSerializer::class) val startTime: LocalTime,
    @Serializable(with = LocalTimeSerializer::class) val endTime: LocalTime,
)