package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.catalog.api.ProductCatalog
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Proves atomic stock UPDATE prevents overselling under parallel checkout-like calls.
 */
@SpringBootTest
@ActiveProfiles("test")
class StockConcurrencyTest {
    private val log = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var productCatalog: ProductCatalog

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Test
    fun `decreaseStock does not change stock for inactive product`() {
        val product =
            productRepository.findAll().firstOrNull()
                ?: error("No seeded products — DataInitializer must run in test profile")
        val id = product.id ?: error("Product id null")
        val originalStock = product.stockQuantity
        val originalActive = product.active

        try {
            product.active = false
            productRepository.saveAndFlush(product)

            assertThrows<BadRequestException> {
                productCatalog.decreaseStock(id, 1)
            }

            assertEquals(
                originalStock,
                productRepository.findById(id).orElseThrow().stockQuantity,
                "inactive product stock must not be decremented",
            )
        } finally {
            productRepository.findById(id).orElseThrow().apply {
                active = originalActive
                stockQuantity = originalStock
                productRepository.saveAndFlush(this)
            }
        }
    }

    @Test
    fun `concurrent decreaseStock never oversells last unit`() {
        val product =
            productRepository.findAll().firstOrNull()
                ?: error("No seeded products — DataInitializer must run in test profile")
        val id = product.id ?: error("Product id null")
        val originalStock = product.stockQuantity

        product.stockQuantity = 1
        productRepository.saveAndFlush(product)

        val pool = Executors.newFixedThreadPool(12)
        try {
            val success = AtomicInteger(0)
            val insufficient = AtomicInteger(0)
            val otherErrors = AtomicInteger(0)
            val unexpected = ConcurrentLinkedQueue<String>()
            val start = CountDownLatch(1)
            val done = CountDownLatch(12)

            repeat(12) {
                pool.submit {
                    try {
                        start.await(10, TimeUnit.SECONDS)
                        productCatalog.decreaseStock(id, 1)
                        success.incrementAndGet()
                    } catch (_: BadRequestException) {
                        insufficient.incrementAndGet()
                    } catch (e: NotFoundException) {
                        otherErrors.incrementAndGet()
                        unexpected.add("${e.javaClass.simpleName}: ${e.message ?: ""}")
                        log.warn("Unexpected NotFoundException in stock concurrency test", e)
                    } catch (e: Exception) {
                        otherErrors.incrementAndGet()
                        unexpected.add("${e.javaClass.simpleName}: ${e.message ?: ""}")
                        log.warn("Unexpected exception in stock concurrency test", e)
                    } finally {
                        done.countDown()
                    }
                }
            }

            start.countDown()
            assertTrue(done.await(30, TimeUnit.SECONDS), "workers timed out")

            assertEquals(
                0,
                otherErrors.get(),
                "unexpected exceptions: ${unexpected.joinToString("; ")}",
            )
            assertEquals(1, success.get(), "exactly one decrease must succeed")
            assertEquals(11, insufficient.get(), "remaining must fail with insufficient stock")

            val remaining = productRepository.findById(id).orElseThrow().stockQuantity
            assertEquals(0, remaining, "stock must be zero after exclusive claim")
        } finally {
            pool.shutdownNow()
            productRepository.findById(id).orElseThrow().apply {
                stockQuantity = originalStock
                productRepository.saveAndFlush(this)
            }
        }
    }
}
