package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperationManagerTest {

    @Test
    fun concurrentOperationsReceiveTheirOwnResult() = runTest {
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("same-address", "first"))
        }
        val firstRequest = OperationManager.queueChannel.receive()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("same-address", "second"))
        }
        val secondRequest = OperationManager.queueChannel.receive()

        secondRequest.result.complete(OperationResult.Connect("same-address", true, "second-result"))
        firstRequest.result.complete(OperationResult.Connect("same-address", true, "first-result"))

        assertEquals("first-result", first.await()?.obj)
        assertEquals("second-result", second.await()?.obj)
    }

    @Test
    fun cancellingOperationCancelsPendingRequest() = runTest {
        val operation = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("address", "device"))
        }
        val request = OperationManager.queueChannel.receive()

        operation.cancelAndJoin()

        assertTrue(request.result.isCancelled)
    }
}
