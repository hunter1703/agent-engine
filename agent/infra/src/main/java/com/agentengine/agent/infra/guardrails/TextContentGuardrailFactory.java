package com.agentengine.agent.infra.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailRuleType;
import com.agentengine.util.agents.beans.config.TextContentGuardrailRule;
import jakarta.inject.Singleton;

@Singleton
public final class TextContentGuardrailFactory implements GuardrailFactory<TextContentGuardrailRule> {

    @Override
    public GuardrailRuleType type() {
        return GuardrailRuleType.TEXT_CONTENT;
    }

    @Override
    public Guardrail create(final TextContentGuardrailRule rule) {
        if (rule == null) {
            return null;
        }
        return new TextContentGuardrail(rule);
    }
}
