rootProject.name = "QMarket"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Client apps
include(":app:androidApp")
include(":app:desktopApp")
include(":app:shared")
include(":app:webApp")

// Shared domain
include(":core")

// Server - modular monolith (Spring Boot 4.1)
include(":server")
include(":server:common")
include(":server:identity")
include(":server:catalog-api")
include(":server:catalog")
include(":server:cart")
include(":server:order")
