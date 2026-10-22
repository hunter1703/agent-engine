package com.agentengine.agent.infra.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailStage;

/**
 * A single guardrail rule that evaluates input or output for policy violations.
 *
 * <p>Guardrails are evaluated at specific stages ({@link GuardrailStage#INPUT} or {@link
 * GuardrailStage#OUTPUT}) and return a {@link GuardrailDecision} indicating whether to allow, warn,
 * block, or escalate the content.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li><b>Decision semantics:</b> The returned {@link GuardrailDecision} has an action
 *       (ALLOW/WARN/BLOCK/ESCALATE) with a code and message explaining the decision.
 *   <li><b>Severity merging:</b> When multiple guardrails evaluate the same context, the
 *       highest-severity decision wins (ESCALATE > BLOCK > WARN > ALLOW).
 * </ul>
 *
 * <h3>Expectations from implementers</h3>
 *
 * <ul>
 *   <li>Guardrail must return a well-formed {@link GuardrailDecision}. Use factory methods ({@code
 *       allow()}, {@code warn()}, {@code block()}, {@code escalate()}) for consistency.
 *   <li>For blocking/escalation decisions, provide a clear {@code code} and {@code message} for the
 *       model to understand why content was rejected.
 *   <li>{@code evaluate()} runs on the model's path, so it should be cheap; a guardrail that
 *       calls a model says so in its own documentation.
 *   <li>An exception thrown by {@code evaluate()} is handled by the policy's error mode.
 * </ul>
 */
public interface Guardrail {
  /** Unique identifier for this guardrail. */
  String id();

  /** Stage at which this guardrail evaluates: input processing or output generation. */
  GuardrailStage stage();

  /**
   * Evaluates the given context and returns a decision.
   *
   * @param context the context to evaluate (text and invocation context)
   * @return a {@link GuardrailDecision} indicating allow, warn, block, or escalate
   */
  GuardrailDecision evaluate(GuardrailContext context);
}
