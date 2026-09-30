package com.github.riley.coreplus.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.riley.coreplus.domain.model.ScheduleLayout
import com.github.riley.coreplus.domain.model.TimeTableDefaults
import com.github.riley.coreplus.domain.model.TimeTableSeason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/** 设置文件的顶层委托。DataStore 要求每个文件只能有一个 `DataStore` 实例，故必须是顶层属性。 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 轻量用户偏好的持久化存储。
 *
 * 技术设计文档 §7 要求「当前作息表的选择需持久化存储，重启后恢复」。
 * 学期开始日期与布局选择同样是「小到一个键、但必须跨启动保留」的数据，因此一并放这里；
 * 结构化数据（课表、作息表内容）仍按文档 §3 走内部存储的 JSON 文件。
 *
 * 所有读取都做了容错：DataStore 抛 [IOException]（磁盘异常）时回退到 [emptyPreferences]，
 * 单个键的值无法解析时回退到默认值。偏好丢失不该让应用起不来。
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 季节未设置时的兜底值：按当前月份推断（5~10 月为夏季，其余为冬季）。
     *
     * 在构造时算一次而不是每次读 Flow 时算，避免跨月份时同一进程内取值发生跳变。
     */
    private val fallbackSeason: TimeTableSeason = TimeTableDefaults.defaultSeason(LocalDate.now())

    private val preferences: Flow<Preferences> =
        context.settingsDataStore.data.catch { throwable ->
            // DataStore 在损坏或磁盘异常时会抛 IOException；这里降级为「全部用默认值」
            if (throwable is IOException) {
                Log.w(TAG, "读取设置失败，本次使用默认值", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }

    /** 当前选中的作息表季节。 */
    val selectedSeason: Flow<TimeTableSeason> = preferences.map { prefs ->
        prefs[Keys.SELECTED_SEASON]?.let { name ->
            TimeTableSeason.entries.firstOrNull { it.name == name }
        } ?: fallbackSeason
    }

    /** 课表布局。 */
    val layout: Flow<ScheduleLayout> = preferences.map { prefs ->
        ScheduleLayout.fromName(prefs[Keys.SELECTED_LAYOUT])
    }

    /** 用户手动设置的学期第一周星期一；null 表示未设置，由 `TermStartInference` 推断。 */
    val termStartDate: Flow<LocalDate?> = preferences.map { prefs ->
        prefs[Keys.TERM_START_DATE]?.let { raw ->
            try {
                LocalDate.parse(raw)
            } catch (e: DateTimeParseException) {
                Log.w(TAG, "学期开始日期 \"$raw\" 无法解析，按未设置处理", e)
                null
            }
        }
    }

    /** 是否已经提示过用户设置学期开始日期。 */
    val termStartPrompted: Flow<Boolean> = preferences.map { prefs ->
        prefs[Keys.TERM_START_PROMPTED] ?: false
    }

    suspend fun setSelectedSeason(season: TimeTableSeason) {
        edit { it[Keys.SELECTED_SEASON] = season.name }
    }

    suspend fun setLayout(layout: ScheduleLayout) {
        edit { it[Keys.SELECTED_LAYOUT] = layout.name }
    }

    /** 传 null 表示清除，恢复自动推断。 */
    suspend fun setTermStartDate(date: LocalDate?) {
        edit { prefs ->
            if (date == null) prefs.remove(Keys.TERM_START_DATE)
            else prefs[Keys.TERM_START_DATE] = date.toString()
        }
    }

    suspend fun setTermStartPrompted(prompted: Boolean) {
        edit { it[Keys.TERM_START_PROMPTED] = prompted }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        runCatching { context.settingsDataStore.edit(block) }
            .onFailure { Log.w(TAG, "写入设置失败", it) }
    }

    private object Keys {
        val SELECTED_SEASON = stringPreferencesKey("selected_season")
        val SELECTED_LAYOUT = stringPreferencesKey("selected_layout")
        val TERM_START_DATE = stringPreferencesKey("term_start_date")
        val TERM_START_PROMPTED = booleanPreferencesKey("term_start_prompted")
    }

    private companion object {
        const val TAG = "SettingsDataStore"
    }
}
