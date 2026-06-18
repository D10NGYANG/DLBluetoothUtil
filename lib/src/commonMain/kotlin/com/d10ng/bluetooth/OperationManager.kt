package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

internal class OperationRequest(
    val operation: OperationType,
    val result: CompletableDeferred<OperationResult> = CompletableDeferred()
)

/**
 * 蓝牙操作管理
 * @Author d10ng
 * @Date 2025/9/29 15:59
 */
internal object OperationManager {

    // 操作任务队列
    val queueChannel = Channel<OperationRequest>(capacity = Channel.BUFFERED)

    /**
     * 执行操作
     * @param operation 操作
     * @return T?
     */
    suspend inline fun <reified T: OperationResult> execute(
        operation: OperationType
    ): T? = withTimeoutOrNull(operation.timeoutMillis.milliseconds) {
        val request = OperationRequest(operation)
        try {
            queueChannel.send(request)
            val result = request.result.await()
            result as? T
        } finally {
            if (!request.result.isCompleted) request.result.cancel()
        }
    }
}
