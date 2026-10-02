package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.PaymentProviderException
import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.domain.PaymentOperationState
import com.kenlikdev.qmarket.order.payment.PaymentGateway
import com.kenlikdev.qmarket.order.payment.StripeApiClient
import com.kenlikdev.qmarket.order.payment.StripeApiException
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
                paymentExpiryProperties = PaymentExpiryProperties(),
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
        assertEquals(null, order.paymentOperationId)
        assertEquals(null, order.paymentOperationStartedAt)
        verify(exactly = 2) { orderRepository.save(order) }
    }

    @Test
    fun `generic payment provider runs outside database transaction`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentCurrency = "rub",
            )
        every { orderRepository.findByIdAndUserIdForUpdate(orderId, userId) } returns order
        every { orderRepository.findByIdForUpdate(orderId) } returns order
        every { orderRepository.save(order) } returns order
        every { paymentGateway.providerId } returns "mock"
        every { paymentGateway.charge(any(), any(), any(), any()) } answers {
            assertEquals(false, transactionActive)
            PaymentChargeResult(
                success = true,
                providerReference = "mock_ref",
            )
        }

        val result = service.pay(userId, orderId)

        assertEquals(OrderStatus.PAID, result.status)
        assertEquals("mock", order.paymentProvider)
        assertEquals("rub", order.paymentCurrency)
        assertEquals("mock_ref", order.paymentProviderReference)
        assertEquals(PaymentOperationState.NONE, order.paymentOperationState)
        assertEquals(null, order.paymentOperationId)
        assertEquals(null, order.paymentOperationStartedAt)
    }

    @Test
    fun `generic provider failure leaves payment operation recoverable`() {
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
        every { paymentGateway.providerId } returns "mock"
        every { paymentGateway.charge(any(), any(), any(), any()) } answers {
            assertEquals(false, transactionActive)
            throw IllegalStateException("provider timeout")
        }

        assertThrows(IllegalStateException::class.java) {
            service.pay(userId, orderId)
        }

        assertEquals(PaymentOperationState.CREATING, order.paymentOperationState)
        assertEquals(true, order.paymentOperationId != null)
        assertEquals(true, order.paymentOperationStartedAt != null)
    }

    @Test
    fun `stale generic payment operation can be retried idempotently`() {
        val operationId = UUID.randomUUID()
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentCurrency = "rub",
                paymentProvider = "mock",
                paymentOperationState = PaymentOperationState.CREATING,
                paymentOperationId = operationId,
                paymentOperationStartedAt = Instant.now().minusSeconds(300),
            )
        every { orderRepository.findByIdAndUserIdForUpdate(orderId, userId) } returns order
        every { orderRepository.findByIdForUpdate(orderId) } returns order
        every { orderRepository.save(order) } returns order
        every { paymentGateway.providerId } returns "mock"
        every { paymentGateway.charge(orderId, userId, BigDecimal("10.50"), "RUB") } returns
            PaymentChargeResult(
                success = true,
                providerReference = "mock_ref",
            )

        val result = service.recoverStaleGenericPaymentOperation(userId, orderId)

        assertEquals(OrderStatus.PAID, result.status)
        assertEquals(PaymentOperationState.NONE, order.paymentOperationState)
        assertEquals(null, order.paymentOperationId)
        assertEquals(null, order.paymentOperationStartedAt)
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
    fun `provider webhook marks order paid during cancellation`() {
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
        every { orderRepository.save(order) } returns order

        val result =
            service.markPaidFromProvider(
                orderId = orderId,
                providerId = "stripe",
                providerReference = "pi_test_123",
                amountMinor = 1050L,
                currency = "rub",
            )

        assertEquals(OrderStatus.PAID, result.status)
        assertEquals(PaymentOperationState.NONE, order.paymentOperationState)
        assertEquals(null, order.paymentOperationId)
        assertEquals(null, order.paymentOperationStartedAt)
    }

    @Test
    fun `provider success wins cancellation race and clears operation state`() {
        val operationId = UUID.randomUUID()
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
                paymentOperationId = operationId,
                paymentOperationStartedAt = Instant.now(),
            )
        every { orderRepository.findByIdForUpdate(orderId) } returns order
        every { orderRepository.save(order) } returns order

        val result =
            service.markPaidFromProvider(
                orderId = orderId,
                providerId = "stripe",
                providerReference = "pi_test_123",
                amountMinor = 1050L,
                currency = "rub",
            )

        assertEquals(OrderStatus.PAID, result.status)
        assertEquals(PaymentOperationState.NONE, order.paymentOperationState)
        assertEquals(null, order.paymentOperationId)
        assertEquals(null, order.paymentOperationStartedAt)
        verify(exactly = 1) { orderRepository.save(order) }
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
    fun `createPaymentSession provider success wins concurrent cancellation`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentCurrency = "rub",
            )
        every { orderRepository.findByIdAndUserIdForUpdate(orderId, userId) } returns order
        every { orderRepository.findByIdForUpdate(orderId) } returns order
        every { orderRepository.save(order) } returns order
        every {
            stripeApi.createPaymentIntentForClient(
                amountMinor = 1050L,
                currency = "rub",
                orderId = orderId,
                userId = userId,
                idempotencyKey = "qmarket-payment-intent-v2-$orderId",
            )
        } answers {
            order.paymentOperationState = PaymentOperationState.CANCELLING
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "requires_payment_method",
                clientSecret = "pi_test_secret",
                amountMinor = 1050L,
                currency = "rub",
            )
        }
        every { stripeApi.retrievePaymentIntent("pi_test_123") } returns
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "requires_payment_method",
                amountMinor = 1050L,
                currency = "rub",
            )
        every { stripeApi.cancelPaymentIntent("pi_test_123") } returns
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "succeeded",
                amountMinor = 1050L,
                currency = "rub",
            )
        every {
            orderRepository.findByIdForUpdate(orderId)
        } returns order

        assertThrows(BadRequestException::class.java) {
            service.createPaymentSession(userId, orderId)
        }

        assertEquals(OrderStatus.PAID, order.status)
        assertEquals(PaymentOperationState.NONE, order.paymentOperationState)
        assertEquals(null, order.paymentOperationId)
        assertEquals(null, order.paymentOperationStartedAt)
    }

    @Test
    fun `createPaymentSession rejects a live payment operation`() {
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("10.50"),
                paymentCurrency = "rub",
                paymentOperationState = PaymentOperationState.CREATING,
                paymentOperationId = UUID.randomUUID(),
                paymentOperationStartedAt = Instant.now(),
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

        assertThrows(PaymentProviderException::class.java) {
            service.createPaymentSession(userId, orderId)
        }

        assertEquals(PaymentOperationState.NONE, order.paymentOperationState)
        assertEquals(null, order.paymentOperationId)
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
        every { stripeApi.retrievePaymentIntent("pi_test_123") } returns
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "requires_payment_method",
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
    fun `already canceled Stripe intent is reconciled as success`() {
        every { stripeApi.retrievePaymentIntent("pi_test_123") } returns
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "canceled",
            )

        val result =
            service.cancelProviderPayment(
                providerId = "stripe",
                providerReference = "pi_test_123",
                paymentOperationKey = null,
                amountMinor = null,
                currency = null,
                orderId = orderId,
                userId = userId,
            )

        assertEquals("canceled", result?.status)
        verify(exactly = 0) { stripeApi.cancelPaymentIntent(any()) }
    }

    @Test
    fun `succeeded Stripe intent is returned for local payment reconciliation`() {
        every { stripeApi.retrievePaymentIntent("pi_test_123") } returns
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "succeeded",
                amountMinor = 1050L,
                currency = "rub",
            )

        val result =
            service.cancelProviderPayment(
                providerId = "stripe",
                providerReference = "pi_test_123",
                paymentOperationKey = null,
                amountMinor = 1050L,
                currency = "rub",
                orderId = orderId,
                userId = userId,
            )

        assertEquals("succeeded", result?.status)
        assertEquals(1050L, result?.amountMinor)
        assertEquals("rub", result?.currency)
        verify(exactly = 0) { stripeApi.cancelPaymentIntent(any()) }
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
