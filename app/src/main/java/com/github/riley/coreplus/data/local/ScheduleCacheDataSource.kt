package com.github.riley.coreplus.data.local

import android.content.Context
import android.util.Log
import com.github.riley.coreplus.di.ApplicationJson
import com.github.riley.coreplus.di.IoDispatcher
import com.github.riley.coreplus.domain.model.Schedule
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
 * 课表的本地 JSON 缓存数据源。
 *
 * 技术设计文档 §3：存储形式为 kotlinx.serialization 的 JSON，文件放在应用内部存储；
 * **缓存不过期**，首次获取后整个学期直接使用。
 *
 * §9 把「缓存文件写入的原子性、异常处理」列为待办，本类一并解决：
 * - **原子写**：先写 `*.tmp`，`fsync` 落盘后再 `renameTo` 覆盖目标文件。
 *   POSIX 的 rename 是原子的且会替换已存在的目标，因此不存在「两个文件都没有」的窗口，
 *   进程在任意时刻被杀都只会留下完整的旧文件或完整的新文件。
 * - **串行化**：[mutex] 保证并发写入不会互相覆盖 tmp 文件。
 *   读取不加锁——rename 的原子性已保证读到的必然是某个完整版本。
 * - **损坏降级**：解析失败时把坏文件备份为 `*.corrupt` 便于排查，然后当作「无缓存」返回 null，
 *   而不是让应用崩溃。
 *
 * 本类是**无状态的磁盘访问器**：不做内存缓存。内存镜像由
 * `data/repository/ScheduleRepositoryImpl` 用 `StateFlow` 持有，避免每次读盘。
 */
@Singleton
class ScheduleCacheDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationJson private val json: Json,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val mutex = Mutex()

    private val cacheFile: File get() = File(context.filesDir, FILE_NAME)

    /** 上一次解析失败时被换下来的文件，仅供人工排查。 */
    private val corruptFile: File get() = File(context.filesDir, "$FILE_NAME.corrupt")

    /**
     * 读取缓存。
     *
     * @return 缓存内容；文件不存在、为空、或无法解析时返回 null
     */
    suspend fun read(): Schedule? = withContext(dispatcher) {
        val file = cacheFile
        if (!file.exists() || file.length() == 0L) return@withContext null

        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "读取缓存文件失败", e)
            return@withContext null
        }

        try {
            json.decodeFromString<Schedule>(text)
        } catch (e: Exception) {
            Log.w(TAG, "缓存文件无法解析，已备份为 $CORRUPT_SUFFIX 并当作无缓存处理", e)
            quarantine(file)
            null
        }
    }

    /**
     * 原子地写入缓存。
     *
     * @return 失败原因；成功时返回 null
     */
    suspend fun write(schedule: Schedule): Throwable? = mutex.withLock {
        withContext(dispatcher) {
            val text = try {
                json.encodeToString(Schedule.serializer(), schedule)
            } catch (e: Exception) {
                Log.e(TAG, "序列化课表失败", e)
                return@withContext e
            }

            try {
                AtomicFileWriter.write(cacheFile, text)
                null
            } catch (e: Exception) {
                Log.e(TAG, "写入缓存文件失败", e)
                e
            }
        }
    }

    /** 删除缓存，回到「无缓存」状态。 */
    suspend fun clear() {
        mutex.withLock {
            withContext(dispatcher) {
                runCatching { cacheFile.delete() }
                runCatching { corruptFile.delete() }
            }
        }
    }

    /** 把坏文件挪到 `.corrupt`，以便下次写入不被它挡住。失败无所谓，不影响主流程。 */
    private fun quarantine(file: File) {
        runCatching {
            if (corruptFile.exists()) corruptFile.delete()
            file.renameTo(corruptFile)
        }.onFailure { Log.w(TAG, "备份损坏缓存失败", it) }
    }

    private companion object {
        const val TAG = "ScheduleCache"
        const val FILE_NAME = "schedule_cache.json"
        const val CORRUPT_SUFFIX = ".corrupt"
    }
}
