package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 蓝牙操作管理
 * @Author d10ng
 * @Date 2025/9/29 15:59
 */
internal object OperationManager {

    // 操作任务队列
    val queueChannel = Channel<OperationType>(capacity = Channel.UNLIMITED)

    // 操作结果事件流
    val resultFlow = MutableSharedFlow<OperationResult>(extraBufferCapacity = Int.MAX_VALUE)

    /**
     * 执行操作
     * @param operation 操作
     * @param predicate 结果过滤
     * @return T?
     */
    suspend inline fun <reified T: OperationResult> execute(
        operation: OperationType,
        crossinline predicate: (T) -> Boolean = { true }
    ): T? = withTimeoutOrNull(operation.timeoutMillis) {
        queueChannel.send(operation)
        resultFlow.first { result ->
            result is T && result.address == operation.address && predicate(result)
        } as T?
    }
}