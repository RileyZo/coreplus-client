package com.github.riley.coreplus.data.repository

import com.github.riley.coreplus.data.local.SettingsDataStore
import com.github.riley.coreplus.domain.model.ScheduleLayout
import com.github.riley.coreplus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsRepository] 的实现，是 [SettingsDataStore] 的薄封装。
 *
 * 存在的意义是让 UI 与 ViewModel 只依赖领域接口，不直接依赖 DataStore；
 * 这样单元测试可以用一个内存实现替换掉它，不必引入 Android 环境。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
) : SettingsRepository {

    override val layout: Flow<ScheduleLayout> = dataStore.layout

    override val showWeekend: Flow<Boolean> = dataStore.showWeekend

    override val termStartDate: Flow<LocalDate?> = dataStore.termStartDate

    override val termStartPrompted: Flow<Boolean> = dataStore.termStartPrompted

    override suspend fun setLayout(layout: ScheduleLayout) {
        dataStore.setLayout(layout)
    }

    override suspend fun setShowWeekend(showWeekend: Boolean) {
        dataStore.setShowWeekend(showWeekend)
    }

    override suspend fun setTermStartDate(date: LocalDate?) {
        dataStore.setTermStartDate(date)
    }

    override suspend fun markTermStartPrompted() {
        dataStore.setTermStartPrompted(true)
    }
}
