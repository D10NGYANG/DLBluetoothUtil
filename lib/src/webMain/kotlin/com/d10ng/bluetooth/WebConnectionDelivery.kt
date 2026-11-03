package com.d10ng.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async

/** Keep an abandoned native Promise owned until it settles, before allowing another attempt. */
internal suspend fun <T> deliverWebConnection(
    scope: CoroutineScope,
    connect: suspend () -> T,
    disconnect: () -> Unit,
    finished: () -> Unit,
): T {
    var abandoned = false
    val pending = scope.async(start = CoroutineStart.UNDISPATCHED) {
        try {
            connect()
        } finally {
            if (abandoned) {
                try { disconnect() } finally { finished() }
            }
        }
    }
    var delivered = false
    try {
        return pending.await().also { delivered = true }
    } finally {
        abandoned = !delivered
        try {
            if (abandoned) disconnect()
        } finally {
            if (pending.isCompleted) finished()
        }
    }
}
