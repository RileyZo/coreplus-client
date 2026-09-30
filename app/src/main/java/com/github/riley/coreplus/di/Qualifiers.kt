package com.github.riley.coreplus.di

import javax.inject.Qualifier

/**
 * 注入 [kotlinx.coroutines.Dispatchers.IO]。
 *
 * 用于一切磁盘读写与阻塞调用。所有数据源都必须带这个限定符，
 * 避免文件 IO 落到主线程或默认线程池上。
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class IoDispatcher

/** 注入 [kotlinx.coroutines.Dispatchers.Default]，用于 CPU 密集的课表构建等计算。 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DefaultDispatcher

/**
 * 注入应用统一使用的 [kotlinx.serialization.json.Json] 实例。
 *
 * 单独加限定符是为了将来可能出现的第二套配置（例如宽松模式的「读旧缓存」专用实例）。
 */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class ApplicationJson
