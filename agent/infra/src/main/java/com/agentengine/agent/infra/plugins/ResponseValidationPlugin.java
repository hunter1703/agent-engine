package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.agent.infra.utils.ResponseUtils;
import com.agentengine.util.agents.ContentUtils;
import com.agentengine.agent.infra.utils.SchemaUtils;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Map;
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
public final class ResponseValidationPlugin extends BasePlugin {

  private static final Logger LOG = LoggerFactory.getLogger(ResponseValidationPlugin.class);
  private static final int MAX_ERRORS = 5;

  private final ConcurrentHashMap<String, Schema> schemaCache = new ConcurrentHashMap<>();

  public ResponseValidationPlugin() {
    super("response_validation_plugin");
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext callbackContext, final LlmResponse response) {

    final InvocationContext context = callbackContext.invocationContext();
    if (!ResponseUtils.isFinalAnswer(response)) {
      return Maybe.empty();
    }
    if (!ContentUtils.hasVisibleText(response.content().orElse(null))) {
      SessionUtils.getSessionState(context).runState()
          .addSignal(
              callbackContext,
              new Signal<>(
                  "empty_response_continuation",
                  "You did not produce a user-facing response or execute any tool calls. "
                      + "Please call the necessary tools to address the user request or provide your final response.",
                  true));
      return Maybe.empty();
    }

    final BaseAgentConfig agentConfig = SessionUtils.agentConfig(context);
    if (agentConfig == null) {
      return Maybe.empty();
    }

    final Map<String, Object> schemaMap = agentConfig.getResponseFormat();
    if (CollectionUtils.isEmpty(schemaMap)) {
      return Maybe.empty();
    }

    final String text =
        response.content().map(Content::text).filter(StringUtils::isNotBlank).orElse(null);

    final String violationMessage = validate(context.agent().name(), schemaMap, text);
    if (violationMessage != null) {
      LOG.info(
          "Response format violation for agent {}: {}", context.agent().name(), violationMessage);
      SessionUtils.getSessionState(context).runState()
          .addSignal(
              callbackContext,
              new Signal<>(
                  "response_format_validation",
                  Violation.builder("response_format_validation").message(violationMessage).build(),
                  true));
    }

    return Maybe.empty();
  }

  private String validate(
      final String agentId, final Map<String, Object> schemaMap, final String text) {
    final JsonNode node = JsonUtils.toJsonNode(text);
    final Schema schema =
        schemaCache.computeIfAbsent(agentId, ignoredKey -> SchemaUtils.buildSchema(schemaMap));
    if (schema == null) {
      return null;
    }

    if (node == null) {
      return "Empty response, not a json";
    }
    final List<Error> errors = schema.validate(text, InputFormat.JSON);
    if (CollectionUtils.isEmpty(errors)) {
      return null;
    }

    final String errorList =
        errors.stream()
            .limit(MAX_ERRORS)
            .map(Error::getMessage)
            .collect(Collectors.joining("\n- ", "- ", ""));
    final String suffix =
        errors.size() > MAX_ERRORS ? "\n- ...and more. Re-read the schema and try again." : "";
    return "Your response was valid JSON but did not match the required schema. Fix these issues:\n"
        + errorList
        + suffix
        + "\nRespond again with only valid JSON matching the schema.";
  }
}
