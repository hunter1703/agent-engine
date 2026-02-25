package com.agentengine.engine.model;

import com.agentengine.engine.utils.Parser;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.BaseLlm;
import java.util.List;
import java.util.Objects;

public abstract class AbstractLLM extends BaseLlm {
  private final Parser parser;
  private final String protocol;
  private final boolean toolCallingEnabled;
  private final boolean parseToolCallsFromText;

  protected AbstractLLM(
      final String model,
      final Parser parser,
      final String protocol,
      final boolean toolCallingEnabled,
      final boolean parseToolCallsFromText) {
    super(model);
    this.parser = Objects.requireNonNull(parser, "parser cannot be null");
    this.protocol = Objects.requireNonNull(protocol, "protocol cannot be null");
    this.toolCallingEnabled = toolCallingEnabled;
    this.parseToolCallsFromText = parseToolCallsFromText;
  }

  public String getProtocol() {
    return protocol;
  }

  public List<RequestProcessor> getRequestProcessors() {
    return List.of(parser);
  }

  public List<ResponseProcessor> getResponseProcessors() {
    return List.of(parser);
  }

  public boolean isToolCallingEnabled() {
    return toolCallingEnabled;
  }

  public boolean isParseToolCallsFromText() {
    return parseToolCallsFromText;
  }
}
