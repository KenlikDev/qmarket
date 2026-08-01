plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktlint)
}

group = "com.kenlikdev.qmarket"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Pure API contract — no Spring, no JPA
}

ktlint {
    version.set("1.5.0")
    android.set(false)
    ignoreFailures.set(false)
}
