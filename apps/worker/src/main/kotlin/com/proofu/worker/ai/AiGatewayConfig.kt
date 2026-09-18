package com.proofu.worker.ai

import com.proofu.ai.AiGateway
import com.proofu.ai.AiGatewayFactory
import com.proofu.ai.AiGatewayProperties
import com.proofu.ai.AiUsageSource
import com.proofu.ai.extraction.RequirementExtractor
import com.proofu.ai.jdbc.JdbcAiExecutionRecorder
import com.proofu.ai.jdbc.JdbcAiUsageSource
import com.proofu.ai.matching.MatchExplainer
import com.proofu.ai.model.ModelClient
import com.proofu.domain.common.IdGenerator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Clock

/** Wires the shared gateway (ADR-0008) with this app's JDBC ledger. Same policy in api and worker. */
@Configuration
class AiGatewayConfig {
    private val log = LoggerFactory.getLogger(AiGatewayConfig::class.java)

    @Bean
    @ConfigurationProperties(prefix = "proofu.ai")
    fun aiGatewayProperties() = AiGatewayProperties()

    @Bean
    fun aiUsageSource(jdbc: JdbcTemplate): AiUsageSource = JdbcAiUsageSource(jdbc)

    @Bean
    fun aiGateway(
        properties: AiGatewayProperties,
        usage: AiUsageSource,
        jdbc: JdbcTemplate,
        clock: Clock,
        ids: IdGenerator,
        /** Tests register a scripted ModelClient bean; production relies on the settings. */
        clientOverride: ObjectProvider<ModelClient>,
    ): AiGateway {
        val settings = properties.toSettings()
        if (settings.provider == "fake") {
            log.warn("AI provider is 'fake': model calls return placeholder JSON and never leave this process")
        }
        val client = clientOverride.ifAvailable ?: AiGatewayFactory.clientFor(settings)
        return AiGatewayFactory.create(settings, usage, JdbcAiExecutionRecorder(jdbc), clock, ids, client)
    }

    @Bean
    fun matchExplainer(gateway: AiGateway) = MatchExplainer(gateway)

    @Bean
    fun requirementExtractor(
        gateway: AiGateway,
        ids: IdGenerator,
    ) = RequirementExtractor(gateway, ids)
}
