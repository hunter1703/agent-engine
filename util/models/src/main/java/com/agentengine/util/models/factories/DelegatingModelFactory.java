package com.agentengine.util.models.factories;

import com.agentengine.util.agents.beans.config.ChatModelConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.EnvUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.models.llm.DelegatingLLMModel;
import com.agentengine.util.models.llm.Parser;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import com.google.adk.models.BaseLlm;
import java.util.HashMap;
import java.util.Map;

public abstract class DelegatingModelFactory<T extends BaseLlm>
    implements ModelFactory<DelegatingLLMModel> {

  @Override
  public final DelegatingLLMModel build(final ModelConfig modelConfig) {
    resolveConfig(modelConfig);
    final ChatModelConfig chatConfig = (ChatModelConfig) modelConfig;
    final boolean toolCallingEnabled = chatConfig.isToolCallingEnabled();
    final Parser parser =
        new Parser(
            buildProtocolMessage(toolCallingEnabled, chatConfig.getInstructions()),
            toolCallingEnabled);
    final T delegate = buildDelegate(chatConfig);
    return new DelegatingLLMModel(delegate, parser);
  }

  protected abstract T buildDelegate(final ChatModelConfig chatConfig);

  private static String buildProtocolMessage(
      final boolean toolCallingEnabled, final String modelInstructions) {
    final Map<String, Object> context = new HashMap<>();
    context.put("toolCallingAllowed", toolCallingEnabled);
    return TemplateUtils.renderTemplateForName("shared/protocol/text.txt", context)
        + "\n\n\n"
        + (StringUtils.isBlank(modelInstructions) ? "" : modelInstructions);
  }

  private static void resolveConfig(final ModelConfig modelConfig) {
    final Template<String> template = TemplateUtils.buildTemplate(modelConfig.getApiKey());
    if (template != null) {
      modelConfig.setApiKey(template.getValue(Map.of("env", EnvUtils.getAll())));
    }
  }
}
