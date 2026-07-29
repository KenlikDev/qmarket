package com.kenlikdev.qmarket.common.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaRepositories(basePackages = ["com.kenlikdev.qmarket"])
@EntityScan(basePackages = ["com.kenlikdev.qmarket"])
@EnableJpaAuditing
open class JpaConfig
