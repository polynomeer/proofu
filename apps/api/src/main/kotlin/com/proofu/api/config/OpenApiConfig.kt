package com.proofu.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(build: BuildProperties?): OpenAPI =
        OpenAPI().info(
            Info()
                .title("ProofU API")
                .version(build?.version ?: "dev")
                .description(
                    "Career source of truth: evidence-linked career data, job matching and traceable application documents.",
                ),
        )
}
