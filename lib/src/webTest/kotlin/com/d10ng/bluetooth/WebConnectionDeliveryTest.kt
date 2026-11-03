package com.d10ng.bluetooth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebConnectionDeliveryTest {
    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun cancelledConnectionIsOwnedUntilLateSuccessIsCleaned() = runTest {
        val native = CompletableDeferred<Unit>()
        var disconnects = 0
        var finished = false
        val caller = async(start = CoroutineStart.UNDISPATCHED) {
            deliverWebConnection(backgroundScope, { native.await() }, { disconnects++ }, { finished = true })
        }
        caller.cancelAndJoin()
        assertEquals(1, disconnects)
        assertFalse(finished)
        native.complete(Unit)
        runCurrent()
        assertEquals(2, disconnects)
        assertTrue(finished)
    }

    @Test
    fun deliveredWebConnectionIsNotDisconnected() = runTest {
        var disconnects = 0
        var finished = false
        val result = deliverWebConnection(backgroundScope, { "server" }, { disconnects++ }, { finished = true })
        assertEquals("server", result)
        assertEquals(0, disconnects)
        assertTrue(finished)
    }

    @Test
    fun cancellationAfterNativeCompletionStillCleansUnclaimedServer() = runTest {
        val native = CompletableDeferred<Unit>()
        var disconnects = 0
        var finished = false
        val nativeScope = CoroutineScope(backgroundScope.coroutineContext + Dispatchers.Unconfined)
        val caller = async(start = CoroutineStart.UNDISPATCHED) {
            deliverWebConnection(nativeScope, { native.await() }, { disconnects++ }, { finished = true })
        }
        native.complete(Unit)
        caller.cancelAndJoin()
        assertEquals(1, disconnects)
        assertTrue(finished)
    }
}
