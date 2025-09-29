package com.d10ng.bluetooth

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import java.lang.ref.WeakReference

/**
 * 当前Activity持有者
 * @Author d10ng
 * @Date 2025/9/29 11:26
 */
internal object CurrentActivityHolder : Application.ActivityLifecycleCallbacks {

    @Volatile
    private var currentActivityRef: WeakReference<ComponentActivity>? = null

    /**
     * 获取当前栈顶 Activity，如果没有则返回 null
     */
    val currentActivity: ComponentActivity?
        get() = currentActivityRef?.get()

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() == activity) {
            currentActivityRef = null
        }
    }

    override fun onActivityPaused(activity: Activity) {
        // 不在这里清理，避免切换 Activity 时拿不到新的 topActivity
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity is ComponentActivity) currentActivityRef = WeakReference(activity)
    }

    // 其他回调不需要实现
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?
    ) { }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle
    ) { }

    override fun onActivityStarted(activity: Activity) { }

    override fun onActivityStopped(activity: Activity) { }
}