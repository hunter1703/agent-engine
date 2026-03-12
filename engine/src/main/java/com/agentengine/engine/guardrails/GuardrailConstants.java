package com.agentengine.engine.guardrails;

public final class GuardrailConstants {
  private GuardrailConstants() {}

  public static final class Code {
    public static final String ALLOW = "guardrail_allow";
    public static final String VIOLATION = "guardrail_violation";
    public static final String RUNTIME_WARN = "guardrail_runtime_warn";
    public static final String RUNTIME_BLOCK = "guardrail_runtime_block";

    public static final String INPUT_LENGTH = "guardrail_input_length";
    public static final String INPUT_PATTERN = "guardrail_input_pattern";
    public static final String OUTPUT_LENGTH = "guardrail_output_length";
    public static final String OUTPUT_PATTERN = "guardrail_output_pattern";
    public static final String OUTPUT_BLOCK = "guardrail_output_block";

    public static final String TOOL_POLICY = "guardrail_tool_policy";
    public static final String TOOL_ESCALATE = "guardrail_tool_escalate";

    public static final String RELEVANCE_STEER = "relevance_steer";
    public static final String RELEVANCE_BLOCK = "relevance_block";

    private Code() {}
  }

  public static final class DetailKey {
    public static final String RULE = "rule";
    public static final String ACTUAL_LENGTH = "actualLength";
    public static final String MAX_LENGTH = "maxLength";
    public static final String TOOL = "tool";
    public static final String RISK = "risk";
    public static final String SCORE = "score";
    public static final String THRESHOLD = "threshold";
    public static final String ATTEMPT = "attempt";
    public static final String RETRY_REQUIRED = "retry_required";

    private DetailKey() {}
  }

  public static final class ToolResultKey {
    public static final String MESSAGE = "message";

    private ToolResultKey() {}
  }
}
