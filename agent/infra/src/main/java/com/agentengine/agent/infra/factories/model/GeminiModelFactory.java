package com.agentengine.agent.infra.factories.model;

import com.agentengine.util.agents.beans.config.ChatModelConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.google.adk.models.Gemini;
import jakarta.inject.Singleton;

@Singleton
public class GeminiModelFactory extends DelegatingModelFactory<Gemini> {

    @Override
    public String type() {
        return ModelConfig.Provider.GEMINI.name();
    }

    @Override
    protected Gemini buildDelegate(final ChatModelConfig chatConfig) {
        return Gemini.builder()
                .modelName(chatConfig.getModel())
                .apiKey(chatConfig.getApiKey())
                .build();
    }
}
