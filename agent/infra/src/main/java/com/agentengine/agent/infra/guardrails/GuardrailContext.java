package com.agentengine.agent.infra.guardrails;

import com.google.adk.agents.InvocationContext;

/**
 * Immutable context passed to guardrails for evaluation.
 *
 * <p>Provides all information a guardrail needs to assess input or output for policy violations in
 * INPUT and OUTPUT stages.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li><b>Immutability:</b> Once constructed, this record cannot be modified.
 *   <li><b>Text presence:</b> {@code text} is always present (the content being evaluated).
 *   <li><b>Invocation context:</b> {@code invocationContext} provides session and agent metadata.
 * </ul>
 */
public record GuardrailContext(String text, InvocationContext invocationContext) {}
