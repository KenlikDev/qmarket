plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.springDependencyManagement)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kotlinSpring)
    alias(libs.plugins.ktlint)
    jacoco
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
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.0")
    }
}

dependencies {
    api(project(":server:common"))
    api(project(":server:catalog-api"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

ktlint {
    version.set("1.5.0")
    android.set(false)
    ignoreFailures.set(false)
}
