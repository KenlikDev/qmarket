package com.kenlikdev.qmarket

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RunCatchingCancellableTest {
    @Test
    fun preservesCancellation() = runTest {
        val cancellation = CancellationException("cancelled")

        val thrown =
            assertFailsWith<CancellationException> {
                runCatchingCancellable<Unit> {
                    throw cancellation
                }
            }

        assertEquals(cancellation, thrown)
    }

    @Test
    fun capturesRegularExceptions() = runTest {
        val result =
            runCatchingCancellable<Unit> {
                error("boom")
            }

        assertEquals("boom", result.exceptionOrNull()?.message)
    }
}
