package com.agentengine.agent.infra.guardrails;

import com.google.adk.agents.InvocationContext;

/**
 * What a guardrail is given to evaluate: the text, the invocation it belongs to, and whether it is
 * the model's final answer (as opposed to text written next to a tool call, or the user's input).
 */
public record GuardrailContext(
    String text, InvocationContext invocationContext, boolean finalAnswer) {}
