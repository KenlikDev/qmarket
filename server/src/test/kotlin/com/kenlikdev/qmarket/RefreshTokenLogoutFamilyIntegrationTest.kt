package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.dto.AuthResponse
import com.kenlikdev.qmarket.identity.dto.RefreshTokenRequest
import com.kenlikdev.qmarket.identity.dto.RegisterRequest
import com.kenlikdev.qmarket.identity.service.AuthService
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Exercises the logout/refresh race against the real service and Testcontainers PostgreSQL.
 *
 * Whichever transaction acquires the user-row lock first may win, but once logout returns
 * no refresh token in the session family may remain usable.
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenLogoutFamilyIntegrationTest {
    @Autowired
    private lateinit var authService: AuthService

    @Test
    fun `concurrent logout and refresh cannot leave an active successor token`() {
        val email = "logout-race-${System.nanoTime()}@test.local"
        val initial =
            authService.register(
                RegisterRequest(
                    email = email,
                    password = "password123",
                ),
            )
        val start = CountDownLatch(1)
        val ready = CountDownLatch(2)
        val done = CountDownLatch(2)
        val refreshResult = AtomicReference<AuthResponse?>()
        val refreshFailure = AtomicReference<Throwable?>()
        val logoutFailure = AtomicReference<Throwable?>()
        val pool = Executors.newFixedThreadPool(2)

        try {
            pool.submit {
                try {
                    ready.countDown()
                    assertTrue(start.await(10, TimeUnit.SECONDS), "refresh start timed out")
                    refreshResult.set(
                        authService.refresh(
                            RefreshTokenRequest(initial.refreshToken),
                        ),
                    )
                } catch (failure: Throwable) {
                    refreshFailure.set(failure)
                } finally {
                    done.countDown()
                }
            }

            pool.submit {
                try {
                    ready.countDown()
                    assertTrue(start.await(10, TimeUnit.SECONDS), "logout start timed out")
                    authService.logout(RefreshTokenRequest(initial.refreshToken))
                } catch (failure: Throwable) {
                    logoutFailure.set(failure)
                } finally {
                    done.countDown()
                }
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "workers did not become ready")
            start.countDown()
            assertTrue(done.await(30, TimeUnit.SECONDS), "logout/refresh workers timed out")

            assertNull(logoutFailure.get(), "logout should complete without an unexpected exception")
            val successor = refreshResult.get()
            if (successor == null) {
                assertTrue(
                    refreshFailure.get() is UnauthorizedException,
                    "if logout wins, refresh must reject the revoked token; " +
                        "actual failure was ${refreshFailure.get()}",
                )
            } else {
                assertNull(refreshFailure.get(), "a successful refresh must not also report failure")
                assertThrows<UnauthorizedException> {
                    authService.refresh(RefreshTokenRequest(successor.refreshToken))
                }
            }
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }
}
