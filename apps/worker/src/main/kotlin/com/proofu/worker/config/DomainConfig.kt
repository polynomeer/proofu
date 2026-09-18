package com.proofu.worker.config

import com.proofu.domain.common.IdGenerator
import com.proofu.domain.common.Uuid7IdGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class DomainConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun idGenerator(): IdGenerator = Uuid7IdGenerator()
}
