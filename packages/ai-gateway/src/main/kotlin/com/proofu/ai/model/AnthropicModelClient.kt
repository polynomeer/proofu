package com.proofu.ai.model

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.DocumentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

/**
 * Anthropic Messages API adapter (ADR-0008).
 *
 * Request shape, in cache order: system prompt (cached) -> documents as `document` blocks
 * (cached, so a snapshot reused by extraction, matching and generation hits the cache) ->
 * the volatile instruction. Output is constrained with `output_config.format` so the text
 * is schema-shaped JSON; the gateway still validates it. Thinking stays adaptive (default).
 */
class AnthropicModelClient(
    private val client: AnthropicClient,
) : ModelClient {
    constructor(apiKey: String) : this(AnthropicOkHttpClient.builder().apiKey(apiKey).build())

    override fun complete(request: ModelRequest): ModelOutcome {
        val params =
            MessageCreateParams
                .builder()
                .model(request.model)
                .maxTokens(request.maxOutputTokens.toLong())
                .systemOfTextBlockParams(
                    listOf(
                        TextBlockParam
                            .builder()
                            .text(request.systemPrompt)
                            .cacheControl(CacheControlEphemeral.builder().build())
                            .build(),
                    ),
                ).addUserMessageOfBlockParams(userBlocks(request))
                .outputConfig(
                    OutputConfig
                        .builder()
                        .effort(effort(request.effort))
                        .format(JsonOutputFormat.builder().schema(schema(request.outputSchema)).build())
                        .build(),
                ).build()

        val message =
            try {
                client.messages().create(params)
            } catch (e: AnthropicException) {
                val status = (e as? AnthropicServiceException)?.statusCode()
                val failure = ProviderFailure.classify(status, e.message)
                throw ModelProviderException(
                    "anthropic request failed (${failure.name.lowercase()}): ${e.message}",
                    e,
                    failure,
                )
            }

        val usage =
            ModelUsage(
                inputTokens = message.usage().inputTokens(),
                outputTokens = message.usage().outputTokens(),
                cacheReadInputTokens = message.usage().cacheReadInputTokens().orElse(0L),
                cacheWriteInputTokens = message.usage().cacheCreationInputTokens().orElse(0L),
            )
        val stop = message.stopReason().orElse(null)
        if (stop == StopReason.REFUSAL) {
            val details = message.stopDetails().orElse(null)
            return ModelOutcome.Refused(
                category = details?.category()?.orElse(null)?.toString(),
                explanation = details?.explanation()?.orElse(null),
                usage = usage,
            )
        }
        val text = message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        return ModelOutcome.Completed(
            text = text,
            model = message.model().toString(),
            usage = usage,
            truncated = stop == StopReason.MAX_TOKENS,
        )
    }

    private fun userBlocks(request: ModelRequest): List<ContentBlockParam> {
        val docs =
            request.documents.mapIndexed { index, doc ->
                val block =
                    DocumentBlockParam
                        .builder()
                        .textSource(doc.text)
                        .title(doc.title)
                        // Tells the model which id to cite; the gateway checks ids against the whitelist.
                        .context("sourceId=${doc.sourceId}")
                if (index == request.documents.lastIndex) block.cacheControl(CacheControlEphemeral.builder().build())
                ContentBlockParam.ofDocument(block.build())
            }
        return docs + ContentBlockParam.ofText(request.instruction)
    }

    private fun effort(effort: Effort): OutputConfig.Effort =
        when (effort) {
            Effort.LOW -> OutputConfig.Effort.LOW
            Effort.MEDIUM -> OutputConfig.Effort.MEDIUM
            Effort.HIGH -> OutputConfig.Effort.HIGH
            Effort.XHIGH -> OutputConfig.Effort.XHIGH
        }

    @Suppress("UNCHECKED_CAST")
    private fun schema(json: String): JsonOutputFormat.Schema {
        val map = MAPPER.readValue(json, Map::class.java) as Map<String, Any?>
        val builder = JsonOutputFormat.Schema.builder()
        map.forEach { (key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)) }
        return builder.build()
    }

    private companion object {
        val MAPPER: JsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()
    }
}
