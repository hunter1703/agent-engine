package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.builders.ModelBuilder;
import com.agentengine.engine.api.utils.ResourceUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.utils.TemplateUtils;
import com.agentengine.engine.model.DelegatingLLMModel;
import com.agentengine.engine.utils.Parser;
import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.HashMap;
import java.util.Map;

public abstract class DelegatingModelBuilder<T extends BaseLlm> implements ModelBuilder<DelegatingLLMModel> {
  private final ConfigRepository configRepository;

  protected DelegatingModelBuilder(final ConfigRepository configRepository) {
    this.configRepository = configRepository;
  }

  @Override
  public final DelegatingLLMModel build(final String agentId, final AgentModelConfig agentModelConfig) {
    final ModelConfig modelConfig = configRepository.loadModelConfig(agentModelConfig.getModelId());
    final boolean toolCallingSupported = modelConfig.isToolCallingSupported();
    final boolean toolCallingEnabled = modelConfig.isToolCallingEnabled();
    final boolean parseToolCallsFromText = !toolCallingSupported;
    final ResponseFormatType responseFormatType = resolveResponseFormatType(modelConfig);
    final Parser parser = Parser.create().withResponseFormat(responseFormatType).toolCallingEnabled(toolCallingEnabled)
        .parseToolCallsFromText(parseToolCallsFromText);
    final String protocol = buildProtocolMessage(responseFormatType, toolCallingEnabled, toolCallingSupported);
    final T delegate = buildDelegate(modelConfig);
    return new DelegatingLLMModel(delegate, parser, protocol, toolCallingEnabled, parseToolCallsFromText);
  }

  protected abstract T buildDelegate(final ModelConfig modelConfig);

  protected static ResponseFormatType resolveResponseFormatType(final ModelConfig config) {
    if (StringUtils.isNotBlank(config.getResponseFormat())) {
      return "json".equalsIgnoreCase(config.getResponseFormat()) ? ResponseFormatType.JSON : ResponseFormatType.TEXT;
    }
    final boolean parseToolCallsFromText = config.isToolCallingEnabled() && !config.isToolCallingSupported();
    return parseToolCallsFromText ? ResponseFormatType.JSON : ResponseFormatType.TEXT;
  }

  private static String buildProtocolMessage(final ResponseFormatType responseFormatType,
      final boolean toolCallingEnabled, final boolean toolCallingSupported) {
    final String templateName = resolveProtocolTemplate(responseFormatType);
    final Map<String, Object> context = new HashMap<>();
    context.put("toolCallingAllowed", toolCallingEnabled);
    context.put("parseToolCallsFromText", !toolCallingSupported);
    if (responseFormatType == ResponseFormatType.JSON) {
      context.put("response_schema", loadReasonerSchema(responseFormatType));
    }
    return TemplateUtils.renderTemplateForName(templateName, context);
  }

  private static String resolveProtocolTemplate(final ResponseFormatType responseFormatType) {
    if (responseFormatType == ResponseFormatType.JSON) {
      return "shared/protocol/json.txt";
    }
    return "shared/protocol/text.txt";
  }

  private static String loadReasonerSchema(final ResponseFormatType responseFormatType) {
    if (responseFormatType != ResponseFormatType.JSON) {
      return "";
    }
    return ResourceUtils.loadResourceAsString("/schemas/shared/response_schema.json");
  }
}
