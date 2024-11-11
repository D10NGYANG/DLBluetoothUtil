package com.d10ng.bluetooth

import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import com.bhm.ble.BleManager
import com.bhm.ble.attribute.BleOptions
import com.bhm.ble.data.BleTaskQueueType

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
        val options = BleOptions.builder()
            .setScanMillisTimeOut(Long.MAX_VALUE)
            .setTaskQueueType(BleTaskQueueType.Default)
            .setOperateMillisTimeOut(6000)
            .setOperateInterval(120)
            .setMtu(500, true)
            .build()
        BleManager.get().init(application, options)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}

internal val ctx by lazy { StartupInitializer.application }