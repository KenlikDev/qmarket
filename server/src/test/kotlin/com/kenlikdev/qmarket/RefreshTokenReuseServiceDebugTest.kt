package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.identity.dto.RefreshTokenRequest
import com.kenlikdev.qmarket.identity.dto.RegisterRequest
import com.kenlikdev.qmarket.identity.service.AuthService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenReuseServiceDebugTest {
    @Autowired
    private lateinit var authService: AuthService

    @Test
    fun refreshServicePath() {
        val registered = authService.register(
            RegisterRequest(
                email = "service-debug-" + System.nanoTime() + "@test.local",
                password = "password123",
            ),
        )
        val refreshed = authService.refresh(RefreshTokenRequest(registered.refreshToken))
        assertNotNull(refreshed.refreshToken)
    }
}
