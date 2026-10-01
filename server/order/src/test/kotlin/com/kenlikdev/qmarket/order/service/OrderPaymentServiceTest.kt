package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.payment.PaymentGateway
import com.kenlikdev.qmarket.order.payment.StripeApiClient
import com.kenlikdev.qmarket.order.payment.StripePaymentIntentResult
import com.kenlikdev.qmarket.order.payment.StripeProperties
import com.kenlikdev.qmarket.order.repository.OrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import java.math.BigDecimal
import java.time.Instant
import com.kenlikdev.qmarket.order.domain.PaymentOperationState
import java.util.UUID

class OrderPaymentServiceTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var notificationService: NotificationService
    private lateinit var paymentGateway: PaymentGateway
    private lateinit var stripeApi: StripeApiClient
    private lateinit var stripeProperties: StripeProperties
    private lateinit var service: OrderPaymentService
    private lateinit var transactionManager: PlatformTransactionManager
    private lateinit var txStatus: TransactionStatus
    private var transactionActive = false
    private val userId = UUID.randomUUID()
    private val orderId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        notificationService = mockk(relaxed = true)
        paymentGateway = mockk()
        stripeApi = mockk()
        stripeProperties =
            StripeProperties(
                secretKey = "sk_test_key",
                publishableKey = "pk_test_key",
                defaultCurrency = "rub",
            )
        val stripeApiProvider = mockk<ObjectProvider<StripeApiClient>>()
        val stripePropertiesProvider = mockk<ObjectProvider<StripeProperties>>()
        every { stripeApiProvider.ifAvailable } returns stripeApi
        every { stripePropertiesProvider.ifAvailable } returns stripeProperties
        transactionManager = mockk()
        txStatus = mockk(relaxed = true)
        every { transactionManager.getTransaction(any()) } answers {
            transactionActive = true
            txStatus
        }
        every { transactionManager.commit(txStatus) } answers {
            transactionActive = false
        }
        every { transactionManager.rollback(txStatus) } answers {
            transactionActive = false
        }
        service =
            OrderPaymentService(
                orderRepository = orderRepository,
                notificationService = notificationService,
                paymentGateway = paymentGateway,
                stripeApiClient = stripeApiProvider,
                stripeProperties = stripePropertiesProvider,
                transactionManager = transactionManager,
            )
    }

    @Test
    fun `createPaymentSession persists stripe payment intent reference`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentCurrency = "rub",
            )
        every { orderRepository.findByIdAndUserIdForUpdate(orderId, userId) } returns order
        every {
            stripeApi.createPaymentIntentForClient(
                amountMinor = 1050L,
                currency = "rub",
                orderId = orderId,
                userId = userId,
                idempotencyKey = "qmarket-payment-intent-v2-$orderId",
            )
        } returns
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "requires_payment_method",
                clientSecret = "pi_test_secret",
            )
        every { orderRepository.save(order) } returns order
        every {
            stripeApi.createPaymentIntentForClient(
                amountMinor = 1050L,
                currency = "rub",
                orderId = orderId,
                userId = userId,
                idempotencyKey = "qmarket-payment-intent-v2-$orderId",
            )
        } returns
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "requires_payment_method",
                clientSecret = "pi_test_secret",
            )

        val result = service.createPaymentSession(userId, orderId)

        assertEquals("stripe", result.providerId)
        assertEquals("pi_test_123", result.paymentIntentId)
        assertEquals("pi_test_123", order.paymentProviderReference)
        assertEquals("stripe", order.paymentProvider)
        assertEquals("rub", order.paymentCurrency)
        assertEquals(PaymentOperationState.NONE, order.paymentOperationState)
        assertEquals(null, order.paymentOperationStartedAt)
        verify(exactly = 2) { orderRepository.save(order) }
    }

    @Test
    fun `pay rejects order while cancellation is in progress`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentOperationState = PaymentOperationState.CANCELLING,
                paymentOperationStartedAt = Instant.now(),
            )
        every { orderRepository.findByIdAndUserIdForUpdate(orderId, userId) } returns order

        assertThrows(BadRequestException::class.java) {
            service.pay(userId, orderId)
        }

        verify(exactly = 0) { paymentGateway.charge(any(), any(), any(), any()) }
    }

    @Test
    fun `provider webhook cannot mark order paid during cancellation`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentProvider = "stripe",
                paymentProviderReference = "pi_test_123",
                paymentCurrency = "rub",
                paymentOperationState = PaymentOperationState.CANCELLING,
                paymentOperationStartedAt = Instant.now(),
            )
        every { orderRepository.findByIdForUpdate(orderId) } returns order

        assertThrows(BadRequestException::class.java) {
            service.markPaidFromProvider(
                orderId = orderId,
                providerId = "stripe",
                providerReference = "pi_test_123",
                amountMinor = 1050L,
                currency = "rub",
            )
        }

        assertEquals(PaymentOperationState.CANCELLING, order.paymentOperationState)
    }

    @Test
    fun `expired order cannot start stripe payment session`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentExpiresAt = Instant.now().minusSeconds(1),
            )
        every { orderRepository.findByIdAndUserIdForUpdate(orderId, userId) } returns order

        assertThrows(BadRequestException::class.java) {
            service.createPaymentSession(userId, orderId)
        }

        verify(exactly = 0) {
            stripeApi.createPaymentIntentForClient(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `failed stripe payment-session creation clears in-flight state`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentCurrency = "rub",
            )
        every { orderRepository.findByIdAndUserIdForUpdate(orderId, userId) } returns order
        every { orderRepository.save(order) } returns order
        every {
            stripeApi.createPaymentIntentForClient(
                amountMinor = 1050L,
                currency = "rub",
                orderId = orderId,
                userId = userId,
                idempotencyKey = "qmarket-payment-intent-v2-$orderId",
            )
        } throws StripeApiException("provider unavailable", 503, null)

        assertThrows(com.kenlikdev.qmarket.common.exception.PaymentProviderException::class.java) {
            service.createPaymentSession(userId, orderId)
        }

        assertEquals(PaymentOperationState.NONE, order.paymentOperationState)
        assertEquals(null, order.paymentOperationStartedAt)
    }

    @Test
    fun `cancelProviderPayment requires stripe cancellation before local cancellation`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentProvider = "stripe",
                paymentProviderReference = "pi_test_123",
            )
        every { stripeApi.cancelPaymentIntent("pi_test_123") } returns
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "canceled",
            )

        service.cancelProviderPayment(
            providerId = "stripe",
            providerReference = "pi_test_123",
            paymentOperationKey = null,
            amountMinor = null,
            currency = null,
            orderId = orderId,
            userId = userId,
        )

        verify(exactly = 1) { stripeApi.cancelPaymentIntent("pi_test_123") }
    }

    @Test
    fun `stripe provider rejects mismatched webhook currency`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentProvider = "stripe",
                paymentProviderReference = "pi_test_123",
                paymentCurrency = "rub",
            )
        every { orderRepository.findByIdForUpdate(orderId) } returns order

        assertThrows(BadRequestException::class.java) {
            service.markPaidFromProvider(
                orderId = orderId,
                providerId = "stripe",
                providerReference = "pi_test_123",
                amountMinor = 1050L,
                currency = "usd",
            )
        }
    }

    @Test
    fun `createPaymentSession uses persisted order currency`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentCurrency = "usd",
            )
        every { orderRepository.findByIdAndUserIdForUpdate(orderId, userId) } returns order
        every { orderRepository.save(order) } returns order
        every {
            stripeApi.createPaymentIntentForClient(
                amountMinor = 1050L,
                currency = "usd",
                orderId = orderId,
                userId = userId,
                idempotencyKey = "qmarket-payment-intent-v2-$orderId",
            )
        } returns StripePaymentIntentResult("pi_usd", "requires_payment_method", "pi_usd_secret")

        val result = service.createPaymentSession(userId, orderId)

        assertEquals("pi_usd", result.paymentIntentId)
        assertEquals("usd", order.paymentCurrency)
    }
}
