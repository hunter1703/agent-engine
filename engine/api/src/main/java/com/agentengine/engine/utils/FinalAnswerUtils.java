package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.agents.InvocationContext;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.Objects;
import java.util.Optional;

public final class FinalAnswerUtils {
  public static final String TOOL_NAME = "submit_final_answer";
  private static final String FINAL_ANSWER_APPROVED_KEY = "final_answer.approved";
  private static final String FINAL_ANSWER_INVOCATION_KEY = "final_answer.invocation_id";

  private FinalAnswerUtils() {}

  public static boolean callsFinalAnswerTool(final Part part) {
    if (part == null) {
      return false;
    }
    final Optional<FunctionCall> functionCall = part.functionCall();
    if (functionCall.isPresent()) {
      return TOOL_NAME.equals(functionCall.get().name().orElse(null));
    }
    final Optional<FunctionResponse> functionResponse = part.functionResponse();
    return functionResponse.filter(response -> TOOL_NAME.equals(response.name().orElse(null))).isPresent();
  }

  public static void syncInvocation(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return;
    }
    final String invocationId = context.invocationId();
    final Object storedInvocation = context.session().state().get(FINAL_ANSWER_INVOCATION_KEY);
    if (StringUtils.isNotBlank(invocationId) && !Objects.equals(invocationId, storedInvocation)) {
      context.session().state().put(FINAL_ANSWER_INVOCATION_KEY, invocationId);
    }
  }

  public static boolean needsFinalAnswer(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return false;
    }
    return Boolean.TRUE.equals(context.session().state().get(FINAL_ANSWER_APPROVED_KEY));
  }

  public static void markNeedsFinalAnswer(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return;
    }
    context.session().state().put(FINAL_ANSWER_APPROVED_KEY, Boolean.TRUE);
  }

  public static void markFinalAnswerComplete(final InvocationContext context) {
    if (context == null || context.session() == null || context.session().state() == null) {
      return;
    }
    context.session().state().put(FINAL_ANSWER_APPROVED_KEY, Boolean.FALSE);
  }
}
