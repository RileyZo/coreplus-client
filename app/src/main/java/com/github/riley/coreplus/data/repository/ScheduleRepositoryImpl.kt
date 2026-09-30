package com.github.riley.coreplus.data.repository

import com.github.riley.coreplus.data.local.SampleScheduleProvider
import com.github.riley.coreplus.data.local.ScheduleCacheDataSource
import com.github.riley.coreplus.data.remote.ApiResult
import com.github.riley.coreplus.data.remote.CorePlusApi
import com.github.riley.coreplus.data.remote.TokenStore
import com.github.riley.coreplus.data.remote.dto.toSchedule
import com.github.riley.coreplus.domain.error.AppError
import com.github.riley.coreplus.domain.error.requiresRelogin
import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.model.Schedule
import com.github.riley.coreplus.domain.repository.RefreshResult
import com.github.riley.coreplus.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ScheduleRepository] 的实现：缓存优先 + 按周合并。
 *
 * **内存镜像**：首次访问时把磁盘缓存读进 [current]，之后常驻。
 * 所有写操作同时更新内存流与磁盘，UI 因此能实时响应本地编辑（如删除课程），
 * 也避免了每次周次切换都读盘。
 *
 * **不会自动联网**：技术设计文档 §5 描述的是「未命中则请求后端」，但拉取必须先登录，
 * 而登录需要用户输入验证码，数据层无法静默完成。因此这里的策略是
 * 「缓存命中即返回，未命中由 UI 引导登录后显式调用 [refresh]」，
 * 首屏判定仍按文档要求由 [hasCache] 提供。
 *
 * **写失败即视为操作失败**：磁盘写入出错时**不更新**内存镜像，
 * 保证「界面显示的」与「已持久化的」始终一致，不会出现重启后数据凭空消失的错觉。
 */
@Singleton
class ScheduleRepositoryImpl @Inject constructor(
    private val cache: ScheduleCacheDataSource,
    private val api: CorePlusApi,
    private val tokenStore: TokenStore,
    private val sampleProvider: SampleScheduleProvider,
) : ScheduleRepository {

    /** 串行化「读-改-写」，避免并发刷新或并发编辑互相覆盖。 */
    private val mutex = Mutex()

    private val current = MutableStateFlow<Schedule?>(null)

    @Volatile
    private var loaded = false

    override val schedule: Flow<Schedule?> = current.onStart { ensureLoaded() }

    override suspend fun cached(): Schedule? {
        ensureLoaded()
        return current.value
    }

    override suspend fun hasCache(): Boolean = cached() != null

    override suspend fun refresh(): RefreshResult {
        // 每次刷新都必须先重新登录（技术设计文档 §1），token 由 AuthRepository 刚刚写入。
        // 没有 token 时不发起请求，避免后端返回 401 再绕一圈。
        val token = tokenStore.token
            ?: return RefreshResult.Failure(
                AppError.LoginRequired(AppError.LoginRequired.Reason.NO_TOKEN),
            )

        return when (val result = api.fetchSchedule(token)) {
            is ApiResult.Error -> {
                // token 或学校会话已失效：清掉，强制下次走完整登录流程
                if (result.error.requiresRelogin) tokenStore.clear()
                RefreshResult.Failure(result.error)
            }

            is ApiResult.Success -> mergeAndPersist(result.data.toSchedule())
        }
    }

    /**
     * 把本次拉取结果按周合并进缓存（技术设计文档 §3「部分成功」）。
     *
     * 响应中出现的周覆盖旧数据，未出现的周保留旧数据；
     * [RefreshResult.Success.missingWeeks] 用于让 UI 提示哪些周至今仍是空白。
     */
    private suspend fun mergeAndPersist(fetched: Schedule): RefreshResult {
        ensureLoaded()
        return mutex.withLock {
            val merged = (current.value ?: Schedule.Empty).merge(fetched)

            val failure = cache.write(merged)
            if (failure != null) return@withLock RefreshResult.Failure(AppError.Storage(failure))

            current.value = merged
            RefreshResult.Success(
                schedule = merged,
                fetchedWeeks = fetched.weeks.map { it.weekNumber }.sorted(),
                missingWeeks = merged.missingWeekNumbers,
            )
        }
    }

    override suspend fun deleteCourse(course: Course): AppError? {
        ensureLoaded()
        return mutex.withLock {
            val snapshot = current.value ?: return@withLock null   // 无缓存时无事可做
            // 目标周不在缓存里（例如已被别的操作改过）时无事可做
            if (snapshot.week(course.week) == null) return@withLock null

            val updated = snapshot.copy(
                weeks = snapshot.weeks.map {
                    if (it.weekNumber == course.week) it.withoutCourse(course) else it
                },
            )

            val failure = cache.write(updated)
            if (failure != null) return@withLock AppError.Storage(failure)

            current.value = updated
            null
        }
    }

    override suspend fun loadSample(): AppError? = mutex.withLock {
        val sample = sampleProvider.schedule()

        val failure = cache.write(sample)
        if (failure != null) return@withLock AppError.Storage(failure)

        current.value = sample
        loaded = true
        null
    }

    /**
     * 惰性把磁盘缓存读进内存，只执行一次。
     *
     * 双重检查：绝大多数调用在 [loaded] 为 true 时直接返回，不竞争锁。
     */
    private suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return@withLock
            current.value = cache.read()
            loaded = true
        }
    }
}
