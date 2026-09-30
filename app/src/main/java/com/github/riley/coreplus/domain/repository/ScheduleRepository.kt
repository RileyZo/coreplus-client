package com.github.riley.coreplus.domain.repository

import com.github.riley.coreplus.domain.error.AppError
import com.github.riley.coreplus.domain.model.Course
import com.github.riley.coreplus.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

/**
 * 课表仓库：技术设计文档 §5 中「按周取数据：先查缓存，命中返回；未命中请求后端并写入」的落点。
 *
 * 与文档的一处刻意差异：本仓库**不会**在缓存未命中时自动发起网络请求。
 * 原因是拉取必须先登录（token 不缓存），而登录需要用户输入验证码，无法在数据层静默完成。
 * 因此实际策略是「缓存命中即返回，未命中则由 UI 引导用户登录后显式调用 [refresh]」。
 * 首屏判定仍按文档要求由本仓库的 [schedule] / [cached] 提供「是否有缓存」。
 */
interface ScheduleRepository {

    /**
     * 缓存的变化流。
     *
     * 初始值为磁盘上的现有缓存（可能为 null，表示无缓存）。
     * 刷新成功、删除课程、载入示例数据后都会重新发射，UI 因此能实时响应本地编辑。
     */
    val schedule: Flow<Schedule?>

    /** 读取当前缓存；无缓存或缓存损坏时返回 null。 */
    suspend fun cached(): Schedule?

    /** 是否已有缓存，供首屏决定「进引导页还是课表页」。 */
    suspend fun hasCache(): Boolean

    /**
     * 用内存中的 token 向后端拉取整学期课表，并按周合并进缓存。
     *
     * token 不存在时不发起请求，直接返回 [RefreshResult.Failure]。
     */
    suspend fun refresh(): RefreshResult

    /** 删除一门课程并**立即覆盖缓存**（技术设计文档 §3：本地编辑直接覆盖，刷新后会丢失）。 */
    suspend fun deleteCourse(course: Course): AppError?

    /**
     * 写入一份示例课表，用于后端未就绪时验证 UI。
     *
     * 仅在 debug 构建中暴露入口，见 `data/local/SampleScheduleProvider`。
     */
    suspend fun loadSample(): AppError?
}

/** [ScheduleRepository.refresh] 的结果。 */
sealed interface RefreshResult {

    /**
     * 拉取成功并已写入缓存。
     *
     * @property schedule 合并后的完整缓存（不是本次响应的原始内容）
     * @property fetchedWeeks 本次成功拉取到并覆盖了缓存的周次
     * @property missingWeeks 本次未拉到的周次，即 `totalWeeks` 减去响应中出现的周，再减去本地已有缓存的周。
     *   非空时代表「部分成功」，UI 应提示用户这些周仍是旧数据或空白
     */
    data class Success(
        val schedule: Schedule,
        val fetchedWeeks: List<Int>,
        val missingWeeks: List<Int>,
    ) : RefreshResult

    /** 拉取失败。此时缓存保持原样，不做任何写入。 */
    data class Failure(val error: AppError) : RefreshResult
}
