package com.agentengine.agent.infra.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailRule;
import com.agentengine.util.agents.beans.config.GuardrailRuleType;

/**
 * Factory for creating {@link Guardrail} instances from configuration.
 *
 * <p>Implementations are registered per rule type and instantiated by the guardrail system when a
 * matching rule type is encountered in agent config.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li><b>Type identification:</b> {@link #type()} identifies what rule type this provider
 *       handles.
 *   <li><b>Factory behavior:</b> {@link #create(GuardrailRule)} either returns a valid {@link
 *       Guardrail} ready to evaluate, or throws an exception if the config is invalid. Never
 *       returns null.
 * </ul>
 *
 * <h3>Expectations from implementers</h3>
 *
 * <ul>
 *   <li>Providers are typically singletons (stateless factories).
 *   <li>{@code create()} must validate rule config and throw exceptions (e.g., {@code
 *       IllegalArgumentException}) if config is invalid.
 *   <li>The returned {@link Guardrail} must be ready to evaluate immediately.
 * </ul>
 *
 * @param <T> the guardrail rule configuration type this provider accepts
 */
public interface GuardrailFactory<T extends GuardrailRule> {
    /** Returns the rule type this provider handles. */
    GuardrailRuleType type();

    /**
     * Creates a {@link Guardrail} instance from the given rule configuration.
     *
     * @param rule the rule configuration
     * @return a new {@link Guardrail} ready to evaluate
     * @throws IllegalArgumentException if rule config is invalid or incomplete
     */
    Guardrail create(T rule);
}
