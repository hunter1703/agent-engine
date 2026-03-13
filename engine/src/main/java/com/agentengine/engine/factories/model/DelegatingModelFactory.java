package com.agentengine.engine.factories.model;

import com.agentengine.engine.agents.processors.Parser;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.model.DelegatingLLMModel;
import com.agentengine.engine.plugin.factories.ModelFactory;
import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.TemplateUtils;
import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.HashMap;
import java.util.Map;

public abstract class DelegatingModelFactory<T extends BaseLlm> implements ModelFactory<DelegatingLLMModel> {

  @Override
  public final DelegatingLLMModel build(final ModelConfig modelConfig) {
    final boolean toolCallingEnabled = modelConfig.isToolCallingEnabled();
    final ResponseFormatType responseFormatType = resolveResponseFormatType(modelConfig);
    final String protocol = buildProtocolMessage(responseFormatType, toolCallingEnabled, modelConfig.getInstructions());
    final Parser parser = new Parser(protocol, toolCallingEnabled);
    final T delegate = buildDelegate(modelConfig);
    return new DelegatingLLMModel(delegate, parser);
  }

  protected abstract T buildDelegate(final ModelConfig modelConfig);

  protected static ResponseFormatType resolveResponseFormatType(final ModelConfig config) {
    return ResponseFormatType.TEXT;
  }

  private static String buildProtocolMessage(final ResponseFormatType responseFormatType, final boolean toolCallingEnabled,
      final String modelInstructions) {
    final String templateName = resolveProtocolTemplate(responseFormatType);
    final Map<String, Object> context = new HashMap<>();
    context.put("toolCallingAllowed", toolCallingEnabled);
    if (responseFormatType == ResponseFormatType.JSON) {
      context.put("response_schema", loadReasonerSchema(responseFormatType));
    }
    return TemplateUtils.renderTemplateForName(templateName, context) + "\n\n\n"
        + (StringUtils.isBlank(modelInstructions) ? "" : modelInstructions);
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
