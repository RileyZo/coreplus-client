package com.github.riley.coreplus.di

import com.github.riley.coreplus.data.repository.AuthRepositoryImpl
import com.github.riley.coreplus.data.repository.ScheduleRepositoryImpl
import com.github.riley.coreplus.data.repository.SettingsRepositoryImpl
import com.github.riley.coreplus.data.repository.TimeTableRepositoryImpl
import com.github.riley.coreplus.domain.repository.AuthRepository
import com.github.riley.coreplus.domain.repository.ScheduleRepository
import com.github.riley.coreplus.domain.repository.SettingsRepository
import com.github.riley.coreplus.domain.repository.TimeTableRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * 数据层的基础依赖。
 *
 * 技术设计文档 §7 要求 Repository、缓存数据源、作息表数据源均为**单例**：
 * - 缓存数据源单例：内部的 `Mutex` 才能真正串行化写入，多实例会各自持有不同的锁
 * - Repository 单例：内存镜像（`StateFlow`）必须全局唯一，否则本地编辑无法实时反映到 UI
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * 应用统一使用的 [Json] 实例，同时服务于「网络响应解析」与「本地缓存读写」。
     *
     * 各配置项的理由：
     * - `ignoreUnknownKeys`：后端可以自由增加响应字段而不破坏老客户端（API.md §1）
     * - `coerceInputValues`：非空字段收到 `null` 时回退到默认值，而不是整份数据解析失败
     * - `isLenient`：学校教务系统的数据经后端转发，可能出现带引号的数字等宽松写法
     * - `explicitNulls = false`：请求体不写值为 null 的字段，减少后端校验负担
     * - `encodeDefaults = true`：缓存文件写全所有字段，便于人工查看与将来加字段时保持兼容
     */
    @Provides
    @Singleton
    @ApplicationJson
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = false
    }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

/** 领域接口 → 实现的绑定。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindTimeTableRepository(impl: TimeTableRepositoryImpl): TimeTableRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
