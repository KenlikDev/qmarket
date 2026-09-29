package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
@ActiveProfiles("test")
class UserOptimisticLockingIntegrationTest {
    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var executor: java.util.concurrent.ExecutorService

    @BeforeEach
    fun setUp() {
        executor = Executors.newFixedThreadPool(2)
    }

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun `concurrent user updates produce an optimistic locking conflict`() {
        val user =
            userRepository.save(
                User(
                    email = "optimistic-${UUID.randomUUID()}@test.local",
                    passwordHash = "test-hash",
                    firstName = "Before",
                    lastName = "User",
                ),
            )
        val userId = requireNotNull(user.id)

        val loaded = CountDownLatch(2)
        val start = CountDownLatch(1)
        val transactionTemplate = TransactionTemplate(transactionManager)

        val futures =
            listOf("Alice", "Bob").map { firstName ->
                executor.submit {
                    transactionTemplate.executeWithoutResult {
                        val current =
                            userRepository.findById(userId).orElseThrow {
                                AssertionError("User disappeared during concurrency test")
                            }
                        loaded.countDown()
                        check(start.await(10, TimeUnit.SECONDS)) { "Concurrent test start timed out" }
                        current.firstName = firstName
                        userRepository.save(current)
                    }
                }
            }

        assertTrue(loaded.await(10, TimeUnit.SECONDS))
        start.countDown()

        val outcomes = futures.map { future -> runCatching { future.get(15, TimeUnit.SECONDS) } }
        val failures = outcomes.mapNotNull { it.exceptionOrNull() }

        assertEquals(1, failures.size)
        assertTrue(hasOptimisticLockFailure(failures.single()))
        assertEquals(1, userRepository.findById(userId).get().version)
    }

    @Test
    fun `stale writer cannot overwrite a committed concurrent change`() {
        val user =
            userRepository.save(
                User(
                    email = "optimistic-stale-" + UUID.randomUUID() + "@test.local",
                    passwordHash = "test-hash",
                    firstName = "Before",
                    lastName = "User",
                ),
            )
        val userId = requireNotNull(user.id)

        val first = userRepository.findById(userId).orElseThrow()
        val second = userRepository.findById(userId).orElseThrow()
        first.firstName = "Committed"
        userRepository.save(first)

        second.firstName = "Stale"
        val failure = runCatching { userRepository.saveAndFlush(second) }.exceptionOrNull()

        assertTrue(hasOptimisticLockFailure(failure ?: error("expected optimistic-lock failure")))
        assertEquals("Committed", userRepository.findById(userId).orElseThrow().firstName)
    }

    private fun hasOptimisticLockFailure(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is OptimisticLockingFailureException) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
