package com.github.riley.coreplus.data.repository

import com.github.riley.coreplus.data.local.SettingsDataStore
import com.github.riley.coreplus.data.local.TimeTableDataSource
import com.github.riley.coreplus.domain.model.TimeTable
import com.github.riley.coreplus.domain.model.TimeTableDefaults
import com.github.riley.coreplus.domain.model.TimeTableSeason
import com.github.riley.coreplus.domain.repository.TimeTableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TimeTableRepository] 的实现。
 *
 * 两份数据分开存：**作息表内容**在 JSON 文件里（[TimeTableDataSource]），
 * **当前选中的季节**在 DataStore 里（[SettingsDataStore]）。
 * 后者是技术设计文档 §7 明确要求持久化的偏好项，与前者生命周期不同——
 * 用户改回默认值时只重写内容文件，不该影响他选的是夏季还是冬季。
 *
 * 与 `ScheduleRepositoryImpl` 一样持有内存镜像，避免每次切换季节都读盘。
 */
@Singleton
class TimeTableRepositoryImpl @Inject constructor(
    private val dataSource: TimeTableDataSource,
    private val settings: SettingsDataStore,
) : TimeTableRepository {

    private val mutex = Mutex()

    private val current = MutableStateFlow<Map<TimeTableSeason, TimeTable>>(emptyMap())

    @Volatile
    private var loaded = false

    override val selectedSeason: Flow<TimeTableSeason> = settings.selectedSeason

    override val timeTables: Flow<Map<TimeTableSeason, TimeTable>> =
        current.onStart { ensureLoaded() }

    override suspend fun select(season: TimeTableSeason) {
        settings.setSelectedSeason(season)
    }

    override suspend fun updateSection(
        season: TimeTableSeason,
        bigSectionIndex: Int,
        sectionIndex: Int,
        start: LocalTime,
        end: LocalTime,
    ): Boolean {
        // 结束时间必须晚于开始时间，否则大节的起止区间会颠倒，
        // 进而让课程块的时间显示成「19:00–08:00」这种 nonsense
        if (start >= end) return false

        ensureLoaded()
        return mutex.withLock {
            val table = current.value[season] ?: TimeTableDefaults.of(season)
            val bigSection = table.bigSection(bigSectionIndex) ?: return@withLock false
            if (bigSection.sections.none { it.index == sectionIndex }) return@withLock false

            val updated = table.copy(
                bigSections = table.bigSections.map { section ->
                    if (section.index != bigSectionIndex) {
                        section
                    } else {
                        section.copy(
                            sections = section.sections.map { part ->
                                if (part.index == sectionIndex) part.copy(startTime = start, endTime = end)
                                else part
                            },
                        )
                    }
                },
            )

            if (dataSource.write(updated) != null) return@withLock false

            current.value = current.value + (season to updated)
            true
        }
    }

    override suspend fun restoreDefaults(season: TimeTableSeason): Boolean {
        ensureLoaded()
        return mutex.withLock {
            val defaults = TimeTableDefaults.of(season)
            if (dataSource.write(defaults) != null) return@withLock false

            current.value = current.value + (season to defaults)
            true
        }
    }

    /** 惰性把两套作息表读进内存，只执行一次。文件缺失时数据源会回退到内置默认值。 */
    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return@withLock
            current.value = dataSource.readAll()
            loaded = true
        }
    }
}
