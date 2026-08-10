package com.d10ng.bluetooth

import com.d10ng.bluetooth.constant.OperationResult
import com.d10ng.bluetooth.constant.OperationType
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperationManagerTest {

    @Test
    fun operationsForSameAddressAreSerialized() = runTest {
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("same-address", "first"))
        }
        val firstRequest = OperationManager.queueChannel.receive()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("same-address", "second"))
        }

        assertFalse(OperationManager.queueChannel.tryReceive().isSuccess)
        firstRequest.result.complete(OperationResult.Connect("same-address", true, "first-result"))
        assertEquals("first-result", first.await()?.obj)

        val secondRequest = OperationManager.queueChannel.receive()
        secondRequest.result.complete(OperationResult.Connect("same-address", true, "second-result"))
        assertEquals("second-result", second.await()?.obj)
    }

    @Test
    fun operationsForDifferentAddressesCanRunConcurrently() = runTest {
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("first-address", "first"))
        }
        val firstRequest = OperationManager.queueChannel.receive()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("second-address", "second"))
        }
        val secondRequest = OperationManager.queueChannel.receive()

        secondRequest.result.complete(OperationResult.Connect("second-address", true, "second-result"))
        firstRequest.result.complete(OperationResult.Connect("first-address", true, "first-result"))

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
        OperationManager.markRecovered("address")
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun cancelledNativeOperationBlocksSameAddressUntilRecovery() = runTest {
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("recovering-address", "first"))
        }
        val firstRequest = OperationManager.queueChannel.receive()
        first.cancelAndJoin()

        val next = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("recovering-address", "next"))
        }
        runCurrent()
        assertFalse(OperationManager.queueChannel.tryReceive().isSuccess)

        OperationManager.markRecovered("recovering-address")
        val nextRequest = OperationManager.queueChannel.receive()
        nextRequest.result.complete(OperationResult.Connect("recovering-address", true, "result"))

        assertTrue(firstRequest.result.isCancelled)
        assertEquals("result", next.await()?.obj)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun operationWaitingForAddressLockRechecksRecoveryAfterPreviousCancellation() = runTest {
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("recheck-address", "first"))
        }
        val firstRequest = OperationManager.queueChannel.receive()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("recheck-address", "waiting"))
        }

        first.cancelAndJoin()
        runCurrent()

        assertTrue(firstRequest.result.isCancelled)
        assertFalse(OperationManager.queueChannel.tryReceive().isSuccess)

        OperationManager.markRecovered("recheck-address")
        val waitingRequest = OperationManager.queueChannel.receive()
        waitingRequest.result.complete(OperationResult.Connect("recheck-address", true, "result"))

        assertEquals("result", waiting.await()?.obj)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun timeoutIncludesWaitingForSameAddressLock() = runTest {
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.Connect>(OperationType.Connect("busy-address", "first"))
        }
        val firstRequest = OperationManager.queueChannel.receive()
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            OperationManager.execute<OperationResult.MtuChanged>(
                OperationType.MtuChanged("busy-address", 517, "second")
            )
        }

        advanceTimeBy(1_001)
        runCurrent()

        assertNull(waiting.await())
        assertFalse(OperationManager.queueChannel.tryReceive().isSuccess)
        first.cancelAndJoin()
        assertTrue(firstRequest.result.isCancelled)
        OperationManager.markRecovered("busy-address")
    }
}
