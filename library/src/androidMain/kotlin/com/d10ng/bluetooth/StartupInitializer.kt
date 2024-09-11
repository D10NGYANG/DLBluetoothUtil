package com.d10ng.bluetooth

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

/**
 * 启动初始化
 * @Author d10ng
 * @Date 2024/9/10 15:37
 */
internal class StartupInitializer : Initializer<Unit> {

    companion object {
        lateinit var application: Application
    }

    override fun create(context: Context) {
        application = context as Application
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}

internal val ctx by lazy { StartupInitializer.application }