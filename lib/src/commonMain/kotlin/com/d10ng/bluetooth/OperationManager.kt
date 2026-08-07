package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

internal class OperationRequest(
    val operation: OperationType,
    val result: CompletableDeferred<OperationResult> = CompletableDeferred()
)

/**
 * 移动端原生 BLE 操作的公共调度器。
 *
 * Android 和 iOS 的 GATT/CoreBluetooth 调用是发起操作、等待回调的两阶段过程。调用方通过
 * [execute] 提交请求，平台 Runner 消费 [queueChannel]、发起原生调用并完成请求结果。
 *
 * 同一设备地址的操作在完整的“入队 -> 原生回调/超时”区间内串行，不同地址可以并发。超时由
 * [OperationType.timeoutMillis] 覆盖整个区间；调用协程取消或超时会取消请求结果，Runner 据此
 * 停止等待并执行平台清理。地址锁在最后一个使用者离开后移除，避免重连不同设备时无限增长。
 */
internal object OperationManager {

    /** 由当前平台唯一的 Runner 消费；不得由连接实现直接消费。 */
    val queueChannel = Channel<OperationRequest>(capacity = Channel.BUFFERED)
    private val lockRegistryMutex = Mutex()
    private val operationLocks = mutableMapOf<String, OperationLock>()

    private class OperationLock(
        val mutex: Mutex = Mutex(),
        var users: Int = 0
    )

    /**
     * 执行 [operation] 并将平台结果收窄为 [T]。
     *
     * 超时返回 `null`；平台明确失败通常返回 `result=false` 的具体结果，由连接层转换为公共异常。
     */
    suspend inline fun <reified T: OperationResult> execute(
        operation: OperationType
    ): T? = executeOperation(operation) as? T

    @PublishedApi
    internal suspend fun executeOperation(operation: OperationType): OperationResult? {
        val operationLock = acquireLock(operation.address)
        try {
            return withTimeoutOrNull(operation.timeoutMillis.milliseconds) {
                operationLock.mutex.withLock {
                    val request = OperationRequest(operation)
                    try {
                        queueChannel.send(request)
                        request.result.await()
                    } finally {
                        if (!request.result.isCompleted) request.result.cancel()
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                releaseLock(operation.address, operationLock)
            }
        }
    }

    private suspend fun acquireLock(address: String): OperationLock = lockRegistryMutex.withLock {
        operationLocks.getOrPut(address) { OperationLock() }.also { it.users++ }
    }

    private suspend fun releaseLock(address: String, operationLock: OperationLock) {
        lockRegistryMutex.withLock {
            operationLock.users--
            if (operationLock.users == 0 &&
                !operationLock.mutex.isLocked &&
                operationLocks[address] === operationLock
            ) {
                operationLocks.remove(address)
            }
        }
    }
}
