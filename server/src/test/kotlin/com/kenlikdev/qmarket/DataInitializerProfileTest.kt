package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.config.DataInitializer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotationUtils

class DataInitializerProfileTest {
    @Test
    fun `data initializer is excluded from prod profile`() {
        val profile = AnnotationUtils.findAnnotation(DataInitializer::class.java, org.springframework.context.annotation.Profile::class.java)
        assertFalse(profile?.value?.contains("prod") == false)
        assert(profile != null)
        assert(profile.value.any { it.contains("!prod") })
    }
}
