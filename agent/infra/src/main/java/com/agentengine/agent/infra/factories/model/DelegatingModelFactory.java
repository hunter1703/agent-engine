package com.agentengine.agent.infra.factories.model;

import com.agentengine.agent.infra.agents.processors.Parser;
import com.agentengine.agent.infra.model.DelegatingLLMModel;
import com.agentengine.util.agents.beans.config.ChatModelConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.HashMap;
import java.util.Map;

public abstract class DelegatingModelFactory<T extends BaseLlm> implements ModelFactory<DelegatingLLMModel> {

    @Override
    public final DelegatingLLMModel build(final ModelConfig modelConfig) {
        resolveConfig(modelConfig);
        final ChatModelConfig chatConfig = (ChatModelConfig) modelConfig;
        final boolean toolCallingEnabled = chatConfig.isToolCallingEnabled();
        final ResponseFormatType responseFormatType = resolveResponseFormatType(chatConfig);
        final String protocol =
                buildProtocolMessage(responseFormatType, toolCallingEnabled, chatConfig.getInstructions());
        final Parser parser = new Parser(protocol, toolCallingEnabled);
        final T delegate = buildDelegate(chatConfig);
        return new DelegatingLLMModel(delegate, parser);
    }

    protected abstract T buildDelegate(final ChatModelConfig chatConfig);

    protected static ResponseFormatType resolveResponseFormatType(final ChatModelConfig config) {
        return ResponseFormatType.TEXT;
    }

    private static String buildProtocolMessage(
            final ResponseFormatType responseFormatType,
            final boolean toolCallingEnabled,
            final String modelInstructions) {
        final String templateName = resolveProtocolTemplate(responseFormatType);
        final Map<String, Object> context = new HashMap<>();
        context.put("toolCallingAllowed", toolCallingEnabled);
        if (responseFormatType == ResponseFormatType.JSON) {
            context.put("response_schema", loadReasonerSchema(responseFormatType));
        }
        return TemplateUtils.renderTemplateForName(templateName, context)
                + "\n\n\n"
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

    private static void resolveConfig(final ModelConfig modelConfig) {
        final Template<String> template = TemplateUtils.buildTemplate(modelConfig.getApiKey());
        modelConfig.setApiKey(template.getValue(Map.of("env", System.getenv())));
    }
}
