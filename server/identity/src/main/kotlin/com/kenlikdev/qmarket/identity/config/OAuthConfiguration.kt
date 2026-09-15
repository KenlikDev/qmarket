package com.kenlikdev.qmarket.identity.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(GoogleOAuthProperties::class)
class OAuthConfiguration
