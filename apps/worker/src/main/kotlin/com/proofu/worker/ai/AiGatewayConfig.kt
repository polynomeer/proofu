package com.proofu.worker.ai

import com.proofu.ai.AiGateway
import com.proofu.ai.AiGatewayFactory
import com.proofu.ai.AiGatewayProperties
import com.proofu.ai.AiUsageSource
import com.proofu.ai.MeteredAiExecutionRecorder
import com.proofu.ai.documents.DocumentDrafter
import com.proofu.ai.extraction.CareerImportExtractor
import com.proofu.ai.extraction.RequirementExtractor
import com.proofu.ai.jdbc.JdbcAiExecutionRecorder
import com.proofu.ai.jdbc.JdbcAiUsageSource
import com.proofu.ai.matching.MatchExplainer
import com.proofu.ai.model.ModelClient
import com.proofu.ai.revision.SentenceReviser
import com.proofu.domain.common.IdGenerator
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
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
        environment: Environment,
        registry: MeterRegistry,
    ): AiGateway {
        val settings =
            properties.toSettings().requireRealProviderWhen(environment.acceptsProfiles(Profiles.of("production")))
        if (settings.provider == "fake") {
            log.warn("AI provider is 'fake': model calls return placeholder JSON and never leave this process")
        }
        val client = clientOverride.ifAvailable ?: AiGatewayFactory.clientFor(settings)
        // The alert for the daily budget compares spend against this number, so the number has
        // to be scrapable too (docs/operations/monitoring.md §비용).
        Gauge
            .builder("proofu.ai.daily.budget.usd") { settings.deploymentDailyBudgetUsd }
            .description("Deployment-wide daily AI budget in USD")
            .strongReference(true)
            .register(registry)
        val recorder = MeteredAiExecutionRecorder(JdbcAiExecutionRecorder(jdbc), registry)
        return AiGatewayFactory.create(settings, usage, recorder, clock, ids, client)
    }

    @Bean
    fun careerImportExtractor(gateway: AiGateway) = CareerImportExtractor(gateway)

    @Bean
    fun matchExplainer(gateway: AiGateway) = MatchExplainer(gateway)

    @Bean
    fun documentDrafter(gateway: AiGateway) = DocumentDrafter(gateway)

    @Bean
    fun sentenceReviser(gateway: AiGateway) = SentenceReviser(gateway)

    @Bean
    fun requirementExtractor(
        gateway: AiGateway,
        ids: IdGenerator,
    ) = RequirementExtractor(gateway, ids)
}
