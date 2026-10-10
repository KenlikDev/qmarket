package com.kenlikdev.qmarket.order.web

import com.kenlikdev.qmarket.order.payment.PaymentGateway
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PaymentConfigControllerTest {
    private fun mockMvc(providerId: String): MockMvc {
        val gateway = mockk<PaymentGateway>()
        every { gateway.providerId } returns providerId
        return MockMvcBuilders.standaloneSetup(PaymentConfigController(gateway)).build()
    }

    @Test
    fun `GET payment config returns only active provider id`() {
        mockMvc("stripe")
            .perform(get("/api/v1/payments/config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providerId").value("stripe"))
            .andExpect(jsonPath("$.secretKey").doesNotExist())
    }

    @Test
    fun `GET payment config supports mock gateway`() {
        mockMvc("mock")
            .perform(get("/api/v1/payments/config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providerId").value("mock"))
    }
}
