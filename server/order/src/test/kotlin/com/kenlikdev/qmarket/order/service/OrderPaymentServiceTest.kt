package com.kenlikdev.qmarket.order.service

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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.math.BigDecimal
import java.util.UUID

class OrderPaymentServiceTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var notificationService: NotificationService
    private lateinit var paymentGateway: PaymentGateway
    private lateinit var stripeApi: StripeApiClient
    private lateinit var stripeProperties: StripeProperties
    private lateinit var service: OrderPaymentService
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
        service =
            OrderPaymentService(
                orderRepository = orderRepository,
                notificationService = notificationService,
                paymentGateway = paymentGateway,
                stripeApiClient = stripeApiProvider,
                stripeProperties = stripePropertiesProvider,
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
            )
        every { orderRepository.findByIdAndUserIdForUpdate(orderId, userId) } returns order
        every {
            stripeApi.createPaymentIntentForClient(
                amountMinor = 1050L,
                currency = "rub",
                orderId = orderId,
                userId = userId,
            )
        } returns
            StripePaymentIntentResult(
                id = "pi_test_123",
                status = "requires_payment_method",
                clientSecret = "pi_test_secret",
            )
        every { orderRepository.save(order) } returns order

        val result = service.createPaymentSession(userId, orderId)

        assertEquals("stripe", result.providerId)
        assertEquals("pi_test_123", result.paymentIntentId)
        assertEquals("pi_test_123", order.paymentProviderReference)
        assertEquals("stripe", order.paymentProvider)
        verify(exactly = 1) { orderRepository.save(order) }
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

        service.cancelProviderPayment(order)

        verify(exactly = 1) { stripeApi.cancelPaymentIntent("pi_test_123") }
    }
}
