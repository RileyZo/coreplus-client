package com.github.riley.coreplus.domain.repository

import com.github.riley.coreplus.domain.model.ScheduleLayout
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 轻量用户偏好仓库，与课表数据、作息表数据分开管理。
 *
 * 这三个键都很小且需要跨启动保留，因此走 DataStore Preferences；
 * 结构化数据（课表、作息表）仍按技术设计文档 §3 走内部存储的 JSON 文件。
 */
interface SettingsRepository {

    /** 课表布局（表格 / 列表）。 */
    val layout: Flow<ScheduleLayout>

    /** 是否在课表中显示周末两列。 */
    val showWeekend: Flow<Boolean>

    /**
     * 用户手动指定的学期第一周星期一。
     *
     * 为 null 表示**未设置**，此时由 [com.github.riley.coreplus.domain.term.TermStartInference] 实时推断。
     * 刻意不存进课表缓存：刷新与清缓存都不会丢失它，也不会跨学期带进一份过期日期。
     */
    val termStartDate: Flow<LocalDate?>

    /** 是否已经就「设置学期开始日期」提示过用户，用于避免每次启动都弹提示。 */
    val termStartPrompted: Flow<Boolean>

    suspend fun setLayout(layout: ScheduleLayout)

    suspend fun setShowWeekend(showWeekend: Boolean)

    /** 设置学期起始日；传 null 表示恢复自动推断。 */
    suspend fun setTermStartDate(date: LocalDate?)

    suspend fun markTermStartPrompted()
}
