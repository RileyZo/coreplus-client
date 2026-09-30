package com.github.riley.coreplus.data.local

import android.content.Context
import android.util.Log
import com.github.riley.coreplus.di.ApplicationJson
import com.github.riley.coreplus.di.IoDispatcher
import com.github.riley.coreplus.domain.model.TimeTable
import com.github.riley.coreplus.domain.model.TimeTableDefaults
import com.github.riley.coreplus.domain.model.TimeTableSeason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 两套作息时间表（夏季 / 冬季）的本地 JSON 数据源。
 *
 * 技术设计文档 §2.3：结构固定、始终只有两套，用户只能修改小节起止时间；内置默认值，提供「恢复默认」。
 *
 * 设计取舍：
 * - **读取时不写盘**。文件缺失或损坏时直接返回 [TimeTableDefaults] 中的内置值，
 *   不在冷启动路径上产生写入。只有用户真正修改过、或点了「恢复默认」，才会落盘。
 * - **损坏即回退默认值**，并把坏文件挪到 `.corrupt` 供排查。作息表丢失只是时间显示不准，
 *   不值得让用户看到错误。
 * - 写入用 [AtomicFileWriter] 保证原子性，用 [mutex] 串行化并发写。
 */
@Singleton
class TimeTableDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationJson private val json: Json,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val mutex = Mutex()

    /** 读取两套作息表。永远返回完整两项，缺失或损坏的季节回退到内置默认值。 */
    suspend fun readAll(): Map<TimeTableSeason, TimeTable> = withContext(dispatcher) {
        TimeTableSeason.entries.associateWith { readOrDefault(it) }
    }

    /**
     * 写入某一套作息表。
     *
     * @return 失败原因；成功时返回 null
     */
    suspend fun write(timeTable: TimeTable): Throwable? = mutex.withLock {
        withContext(dispatcher) {
            val text = try {
                json.encodeToString(TimeTable.serializer(), timeTable)
            } catch (e: Exception) {
                Log.e(TAG, "序列化${timeTable.season}作息表失败", e)
                return@withContext e
            }
            try {
                AtomicFileWriter.write(fileOf(timeTable.season), text)
                null
            } catch (e: Exception) {
                Log.e(TAG, "写入${timeTable.season}作息表失败", e)
                e
            }
        }
    }

    private fun readOrDefault(season: TimeTableSeason): TimeTable {
        val file = fileOf(season)
        if (!file.exists() || file.length() == 0L) return TimeTableDefaults.of(season)

        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "读取${season}作息表失败，回退默认值", e)
            return TimeTableDefaults.of(season)
        }

        val parsed = try {
            json.decodeFromString<TimeTable>(text)
        } catch (e: Exception) {
            Log.w(TAG, "${season}作息表无法解析，已备份为 .corrupt 并回退默认值", e)
            quarantine(file)
            return TimeTableDefaults.of(season)
        }

        // 文件内容与季节不符（例如手动改坏了文件名），或大节为空，都视为不可用
        if (parsed.season != season || parsed.bigSections.isEmpty()) {
            Log.w(TAG, "${season}作息表内容不匹配，回退默认值")
            return TimeTableDefaults.of(season)
        }
        return parsed
    }

    private fun fileOf(season: TimeTableSeason): File =
        File(context.filesDir, "timetable_${season.name.lowercase()}.json")

    private fun quarantine(file: File) {
        runCatching {
            val corrupt = File(file.parentFile, file.name + ".corrupt")
            if (corrupt.exists()) corrupt.delete()
            file.renameTo(corrupt)
        }.onFailure { Log.w(TAG, "备份损坏的作息表失败", it) }
    }

    private companion object {
        const val TAG = "TimeTableCache"
    }
}
