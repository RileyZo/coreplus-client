package com.github.riley.coreplus.domain.model

import java.time.LocalTime

/**
 * 内置的两套默认作息时间表（夏季 / 冬季）。
 *
 * 「恢复默认」时回退到此处的定义；用户修改只影响持久化后的副本，不改动这里。
 *
 * > 技术设计文档未给出具体时间，下表为常见大学作息（5 个大节、每节 2 小节），需按实际校历调整。
 */
object TimeTableDefaults {

    /** 夏季作息表。 */
    val summer: TimeTable = TimeTable(
        season = TimeTableSeason.SUMMER,
        bigSections = listOf(
            bigSection(1, "08:00" to "08:45", "08:55" to "09:40"),
            bigSection(2, "10:00" to "10:45", "10:55" to "11:40"),
            bigSection(3, "14:00" to "14:45", "14:55" to "15:40"),
            bigSection(4, "16:00" to "16:45", "16:55" to "17:40"),
            bigSection(5, "19:00" to "19:45", "19:55" to "20:40"),
        ),
    )

    /** 冬季作息表。 */
    val winter: TimeTable = TimeTable(
        season = TimeTableSeason.WINTER,
        bigSections = listOf(
            bigSection(1, "08:00" to "08:45", "08:55" to "09:40"),
            bigSection(2, "10:00" to "10:45", "10:55" to "11:40"),
            bigSection(3, "13:30" to "14:15", "14:25" to "15:10"),
            bigSection(4, "15:30" to "16:15", "16:25" to "17:10"),
            bigSection(5, "18:30" to "19:15", "19:25" to "20:10"),
        ),
    )

    /** 两套内置作息表。 */
    val all: List<TimeTable> = listOf(summer, winter)

    /** 取指定季节的默认作息表。 */
    fun of(season: TimeTableSeason): TimeTable = when (season) {
        TimeTableSeason.SUMMER -> summer
        TimeTableSeason.WINTER -> winter
    }
}

/** 由「开始-结束」时间段构造一个大节，小节序号按传入顺序从 1 开始。 */
private fun bigSection(index: Int, vararg ranges: Pair<String, String>): BigSection =
    BigSection(
        index = index,
        sections = ranges.mapIndexed { i, (start, end) ->
            Section(index = i + 1, startTime = LocalTime.parse(start), endTime = LocalTime.parse(end))
        },
    )