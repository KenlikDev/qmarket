package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.config.DataInitializer
import org.junit.jupiter.api.Test
import org.springframework.core.annotation.AnnotationUtils

class DataInitializerProfileTest {
    @Test
    fun `data initializer is excluded from prod profile`() {
        val profile = AnnotationUtils.findAnnotation(DataInitializer::class.java, org.springframework.context.annotation.Profile::class.java)
        requireNotNull(profile) { "DataInitializer must declare an explicit profile exclusion for prod" }
        assert(profile.value.contentEquals(arrayOf("!prod"))) {
            "Expected DataInitializer to be excluded from prod, actual profiles=${profile.value.contentToString()}"
        }
    }
}
