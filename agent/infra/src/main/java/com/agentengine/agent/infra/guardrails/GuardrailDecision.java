package com.agentengine.agent.infra.guardrails;

import com.agentengine.util.agents.beans.config.GuardrailAction;
import com.agentengine.util.common.CollectionUtils;
import java.util.Map;

/**
 * The decision returned by a guardrail evaluation.
 *
 * <p>Encodes the action to take (allow, warn, block, escalate) and contextual information (code,
 * message, details) for logging, debugging, and feedback.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li><b>Action semantics:</b> The action determines flow behavior:
 *       <ul>
 *         <li>{@code ALLOW} — content passes; agent processing continues.
 *         <li>{@code WARN} — content allowed but flagged for observation; agent continues.
 *         <li>{@code BLOCK} — content rejected and replaced with guardrail message. For input
 *             guardrails, the model call is prevented; the agent receives the block message as if
 *             from the model. For output guardrails, the model's response is discarded and replaced
 *             with the block message. Agent session continues.
 *         <li>{@code ESCALATE} — content flagged for escalation; agent session pauses pending human
 *             review decision.
 *       </ul>
 *   <li><b>Merging semantics:</b> When multiple guardrails evaluate the same context, the
 *       highest-severity decision prevails (ESCALATE > BLOCK > WARN > ALLOW). A single blocking
 *       guardrail prevents session even if others allow.
 *   <li><b>Non-null action:</b> Action defaults to {@code ALLOW} if null.
 *   <li><b>Non-null args map:</b> {@code toolArgs} is never null — it's an empty map if absent.
 * </ul>
 */
public record GuardrailDecision(GuardrailAction action, String code, String message, Map<String, Object> details) {

    public GuardrailDecision {
        action = action == null ? GuardrailAction.ALLOW : action;
        details = CollectionUtils.nullSafeMap(details);
    }

    public static GuardrailDecision allow() {
        return new GuardrailDecision(GuardrailAction.ALLOW, GuardrailConstants.Code.ALLOW, "Allowed", Map.of());
    }

    public static GuardrailDecision warn(final String code, final String message) {
        return new GuardrailDecision(GuardrailAction.WARN, code, message, Map.of());
    }

    public static GuardrailDecision block(final String code, final String message) {
        return new GuardrailDecision(GuardrailAction.BLOCK, code, message, Map.of());
    }

    public static GuardrailDecision escalate(final String code, final String message) {
        return new GuardrailDecision(GuardrailAction.ESCALATE, code, message, Map.of());
    }

    public boolean isBlocking() {
        return action == GuardrailAction.BLOCK || action == GuardrailAction.ESCALATE;
    }

    public static GuardrailDecision merge(final GuardrailDecision left, final GuardrailDecision right) {
        final GuardrailDecision lhs = left == null ? GuardrailDecision.allow() : left;
        final GuardrailDecision rhs = right == null ? GuardrailDecision.allow() : right;
        return severity(rhs.action()) > severity(lhs.action()) ? rhs : lhs;
    }

    private static int severity(final GuardrailAction action) {
        return switch (action) {
            case ESCALATE -> 4;
            case BLOCK -> 3;
            case WARN -> 2;
            case ALLOW, UNKNOWN -> 1;
        };
    }
}
