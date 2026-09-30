package com.github.riley.coreplus.domain.repository

import com.github.riley.coreplus.domain.model.TimeTable
import com.github.riley.coreplus.domain.model.TimeTableSeason
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/**
 * 作息时间表仓库。
 *
 * 技术设计文档 §2.3：结构固定，始终只有夏季、冬季两套，用户只能修改内部小节的起止时间。
 * §7：本仓库做成单例注入，且**当前选择的季节需持久化存储，重启后恢复**。
 */
interface TimeTableRepository {

    /** 当前选中的季节，已持久化。 */
    val selectedSeason: Flow<TimeTableSeason>

    /** 两套作息表的当前内容（可能已被用户修改）。 */
    val timeTables: Flow<Map<TimeTableSeason, TimeTable>>

    /** 切换作息表。立即持久化，UI 会随之重算所有课程时间。 */
    suspend fun select(season: TimeTableSeason)

    /**
     * 修改某个小节的起止时间。
     *
     * @return 是否写入成功。`start` 不早于 `end` 时返回 false 且不改动任何数据
     */
    suspend fun updateSection(
        season: TimeTableSeason,
        bigSectionIndex: Int,
        sectionIndex: Int,
        start: LocalTime,
        end: LocalTime,
    ): Boolean

    /**
     * 恢复指定季节的内置默认值（技术设计文档 §2.3），
     * 即重写为 [com.github.riley.coreplus.domain.model.TimeTableDefaults] 中的定义。
     *
     * @return 是否写入成功。磁盘写入失败时返回 false，内存中的值也不会改变
     */
    suspend fun restoreDefaults(season: TimeTableSeason): Boolean
}
