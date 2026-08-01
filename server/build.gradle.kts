plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.springBoot)
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
    implementation(project(":server:common"))
    implementation(project(":server:identity"))
    implementation(project(":server:catalog"))
    implementation(project(":server:cart"))
    implementation(project(":server:order"))
    implementation(project(":core"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.jackson.module.kotlin)

    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    developmentOnly(libs.spring.boot.devtools)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.mockk)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("qmarket-server")
    mainClass.set("com.kenlikdev.qmarket.QMarketApplicationKt")
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
    filter {
        exclude("**/generated/**")
    }
}
