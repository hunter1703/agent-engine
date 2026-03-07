package com.agentengine.engine.tools;

import com.agentengine.engine.api.beans.config.GuardrailAction;
import com.agentengine.engine.api.beans.config.GuardrailErrorMode;
import com.agentengine.engine.api.beans.config.ToolSafetyGuardrailRule;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.guardrails.GuardrailContext;
import com.agentengine.engine.guardrails.GuardrailDecision;
import com.agentengine.engine.guardrails.GuardrailUtils;
import com.agentengine.engine.guardrails.rules.ToolSafetyGuardrail;
import com.agentengine.engine.utils.SessionStateUtils;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.Violation;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.FunctionDeclaration;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Decorates a tool with pre-execution guardrail checks. */
public final class GuardedTool extends BaseTool {
  private final BaseTool delegate;
  private final ToolDescriptor descriptor;
  private final List<ToolSafetyGuardrail> guardrails;
  private final GuardrailErrorMode errorMode;

  public GuardedTool(
      final BaseTool delegate,
      final ToolDescriptor descriptor,
      final List<ToolSafetyGuardrailRule> configs,
      final GuardrailErrorMode errorMode) {
    super(descriptor.name(), descriptor.description(), false);
    this.delegate = delegate;
    this.descriptor = descriptor;
    this.guardrails = configs.stream().map(ToolSafetyGuardrail::new).toList();
    this.errorMode = errorMode;
  }

  @Override
  public Optional<FunctionDeclaration> declaration() {
    return delegate.declaration();
  }

  @Override
  public Single<Map<String, Object>> runAsync(
      final Map<String, Object> args, final ToolContext toolContext) {
    final GuardrailContext guardrailContext =
        GuardrailContext.builder()
            .invocationContext(toolContext == null ? null : toolContext.invocationContext())
            .toolDescriptor(descriptor)
            .toolArgs(args)
            .build();
    final GuardrailDecision decision = GuardrailUtils.evaluate(guardrailContext, guardrails, errorMode);

    if (decision.action() == GuardrailAction.ESCALATE) {
      if (toolContext != null && toolContext.invocationContext() != null) {
        SessionStateUtils.pause(toolContext.invocationContext(), decision.message(), decision.code());
      }
      return Single.just(
          Map.of(
              "status",
              "paused",
              "clarification",
              decision.message(),
              "guardrail_code",
              decision.code()));
    }

    if (decision.action() == GuardrailAction.BLOCK) {
      if (toolContext != null && toolContext.invocationContext() != null) {
        RunStateUtils.getState(toolContext.invocationContext())
            .addViolation(
                Violation.builder(decision.code())
                    .message(decision.message())
                    .correctionMessage(decision.message())
                    .details(decision.details())
                    .build());
      }
      return Single.just(
          Map.of(
              "error",
              decision.message(),
              "guardrail_code",
              decision.code(),
              "status",
              "blocked"));
    }

    if (decision.action() == GuardrailAction.WARN
        && toolContext != null
        && toolContext.invocationContext() != null) {
      RunStateUtils.getState(toolContext.invocationContext())
          .addViolation(
              Violation.builder(decision.code())
                  .message(decision.message())
                  .correctionMessage(decision.message())
                  .details(decision.details())
                  .build());
    }

    return delegate.runAsync(args, toolContext);
  }
}
