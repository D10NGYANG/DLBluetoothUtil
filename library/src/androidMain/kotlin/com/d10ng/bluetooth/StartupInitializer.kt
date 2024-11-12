package com.d10ng.bluetooth

import android.app.Application
import android.content.Context
import androidx.startup.Initializer
import com.bhm.ble.BleManager
import com.bhm.ble.attribute.BleOptions
import com.bhm.ble.data.BleTaskQueueType
import com.clj.fastble.scan.BleScanRuleConfig

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
            //.setTaskQueueType(BleTaskQueueType.Operate)
            //.setOperateMillisTimeOut(6000)
            //.setOperateInterval(60)
            .setMtu(500, true)
            .build()
        //BleManager.get().init(application, options)

        // 蓝牙库初始化
        com.clj.fastble.BleManager.getInstance().init(application)

        // 全局配置
        com.clj.fastble.BleManager.getInstance()
            // 是否允许打印数据
            .enableLog(true)
            // 设置连接时重连次数和重连间隔（毫秒），默认为0次不重连
            .setReConnectCount(0, 300)
            // 设置分包发送的时候，每一包的数据长度，默认20个字节
            //.setSplitWriteNum(200)
            // 设置连接超时时间（毫秒），默认10秒
            .setConnectOverTime(10 * 1000L)
            // 设置readRssi、setMtu、write、read、notify、indicate的超时时间（毫秒），默认5秒
            .operateTimeout = 5 * 1000
        // 配置扫描规则
        val scanRuleConfig = BleScanRuleConfig.Builder()
            // 只扫描指定的服务的设备，可选
            //.setServiceUuids(arrayOf(uuid))
            // 只扫描指定广播名的设备，可选
            //.setDeviceName(true, names)
            // 只扫描指定mac的设备，可选
            //.setDeviceMac(mac)
            // 连接时的autoConnect参数，可选，默认false
            //.setAutoConnect(isAutoConnect)
            // 扫描超时时间，可选，默认7秒；小于等于0表示不限制扫描时间
            .setScanTimeOut(0)
            .build()
        com.clj.fastble.BleManager.getInstance().initScanRule(scanRuleConfig)
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}

internal val ctx by lazy { StartupInitializer.application }