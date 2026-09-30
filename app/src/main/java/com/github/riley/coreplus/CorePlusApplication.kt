package com.github.riley.coreplus

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * 应用入口，负责触发 Hilt 的依赖图生成。
 *
 * 有意保持为空：所有单例（Repository、缓存数据源、作息表数据源、HttpClient）
 * 均由 Hilt 按需惰性创建，不需要在 [onCreate] 中做初始化。
 */
@HiltAndroidApp
class CorePlusApplication : Application()
