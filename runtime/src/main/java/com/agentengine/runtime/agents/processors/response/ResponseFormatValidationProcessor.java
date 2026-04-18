package com.agentengine.runtime.agents.processors.response;

import com.agentengine.runtime.agents.Agent;
import com.agentengine.runtime.utils.ResponseUtils;
import com.agentengine.runtime.utils.RunUtils;
import com.agentengine.runtime.utils.SchemaUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import io.reactivex.rxjava3.core.Single;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates non-partial, final-answer responses against the agent's {@code responseFormat} JSON
 * schema, requesting a correction turn when the response does not conform.
 *
 * <p>Partial responses and agents without a {@code responseFormat} pass through unchanged.
 */
public final class ResponseFormatValidationProcessor implements ResponseProcessor {
    public static final ResponseFormatValidationProcessor INSTANCE = new ResponseFormatValidationProcessor();

    private static final Logger LOG = LoggerFactory.getLogger(ResponseFormatValidationProcessor.class);
    private static final int MAX_ERRORS = 5;

    private final ConcurrentHashMap<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    private ResponseFormatValidationProcessor() {}

    @Override
    public Single<ResponseProcessingResult> processResponse(
            final InvocationContext context, final LlmResponse response) {
        if (response.partial().orElse(false)) {
            return ResponseUtils.single(response);
        }

        if (!(context.agent() instanceof Agent engineAgent)) {
            return ResponseUtils.single(response);
        }

        final Map<String, Object> schemaMap = engineAgent.getAgentConfig().getResponseFormat();
        if (CollectionUtils.isEmpty(schemaMap)) {
            return ResponseUtils.single(response);
        }

        final String text = response.content()
                .map(Content::text)
                .filter(StringUtils::isNotBlank)
                .orElse(null);
        //        if (text == null) {
        //            return ResponseUtils.single(response);
        //        }

        final String violationMessage = validate(context.agent().name(), schemaMap, text);
        if (violationMessage != null) {
            LOG.info(
                    "Response format violation for agent {}: {}",
                    context.agent().name(),
                    violationMessage);
            RunUtils.getOrInitState(context)
                    .requestContinuation(Violation.builder("response_format_validation")
                            .message(violationMessage)
                            .build());
        }

        return ResponseUtils.single(response);
    }

    private String validate(final String agentId, final Map<String, Object> schemaMap, final String text) {
        final JsonNode node = JsonUtils.toJsonNode(text);
        final JsonSchema schema = schemaCache.computeIfAbsent(agentId, _ -> SchemaUtils.buildSchema(schemaMap));
        if (schema == null) {
            return null;
        }

        if (node == null) {
            return "Empty response, not a json";
        }
        final Set<ValidationMessage> errors = schema.validate(node);
        if (CollectionUtils.isEmpty(errors)) {
            return null;
        }

        final String errorList = errors.stream()
                .limit(MAX_ERRORS)
                .map(ValidationMessage::getMessage)
                .collect(Collectors.joining("\n- ", "- ", ""));
        final String suffix = errors.size() > MAX_ERRORS ? "\n- ...and more. Re-read the schema and try again." : "";
        return "Your response was valid JSON but did not match the required schema. Fix these issues:\n"
                + errorList + suffix
                + "\nRespond again with only valid JSON matching the schema.";
    }
}
