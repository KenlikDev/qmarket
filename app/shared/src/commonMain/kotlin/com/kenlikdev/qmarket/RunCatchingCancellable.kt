package com.kenlikdev.qmarket

import kotlinx.coroutines.CancellationException

/**
 * Equivalent to [runCatching] for suspend operations without swallowing coroutine cancellation.
 */
suspend inline fun <T> runCatchingCancellable(
    crossinline block: suspend () -> T,
): Result<T> =
    try {
        Result.success(block())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        Result.failure(exception)
    }
