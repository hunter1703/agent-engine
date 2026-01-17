package com.agentengine.cli;

import com.agentengine.cli.beans.BuildPromptRequest;
import com.agentengine.cli.beans.InvokeAgentRequest;
import com.agentengine.cli.beans.Request;
import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.AgentListener;
import com.agentengine.engine.beans.ToolExecution;
import com.agentengine.engine.beans.config.ConfigLoader;
import com.agentengine.engine.builders.AgentBuilderFactory;
import com.agentengine.engine.message.Message;
import com.agentengine.engine.message.ToolCall;
import com.agentengine.engine.utils.JsonUtils;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;

@QuarkusMain
public final class StdioAgentServer implements QuarkusApplication {
  private AgentEngine agent;
  private String sessionId;

  private final AgentBuilderFactory agentBuilderFactory;
  private final ConfigLoader configLoader;

  @Inject
  public StdioAgentServer(
      final AgentBuilderFactory agentBuilderFactory, final ConfigLoader configLoader) {
    this.agentBuilderFactory = agentBuilderFactory;
    this.configLoader = configLoader;
  }

  public static void main(final String[] args) {
    Quarkus.run(StdioAgentServer.class, args);
  }

  @Override
  public int run(String... args) throws Exception {
    init(args[1], args[2]);
    final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    String line;

    while ((line = reader.readLine()) != null) {
      line = line.trim();
      if (line.isBlank()) {
        continue;
      }
      final Request request = JsonUtils.fromJson(line, Request.class);
      switch (request) {
        case InvokeAgentRequest invokeAgentRequest -> invoke(invokeAgentRequest);
        case BuildPromptRequest buildPromptRequest -> buildPrompt(buildPromptRequest);
        default ->
            throw new IllegalArgumentException(
                STR."Unsupported request type: \{request.getType()}");
      }
    }

    return 0;
  }

  private void init(final String agentName, final String agentConfig) {
    agent =
        agentBuilderFactory
            .getBuilder(agentName)
            .build(agentName, configLoader.loadConfig(Paths.get(agentConfig)));
    sessionId = UUID.randomUUID().toString();
    agent.registerListener(
        new AgentListener() {
          @Override
          public void onToolPlan(final String sessionId, final List<ToolCall> toolCalls) {
            for (ToolCall call : toolCalls) {
              sendEvent(
                  sessionId,
                  "tool_plan",
                  Map.of("tool_name", call.name(), "tool_args", call.args()));
            }
          }

          @Override
          public void onToolExecution(final String sessionId, final ToolExecution toolExecution) {
            sendEvent(
                sessionId,
                "tool_result",
                Map.of(
                    "tool_name", toolExecution.getToolCall().name(),
                    "tool_output", toolExecution.getOutput(),
                    "tool_status", toolExecution.getStatus(),
                    "tool_duration_ms", toolExecution.getDurationMs()));
          }

          @Override
          public void onReasoningStart(final String sessionId) {
            sendEvent(sessionId, "status", "reasoning...");
          }

          @Override
          public void onToolRepair(String sessionId) {
            sendEvent(sessionId, "status", "repairing tool...");
          }
        });
  }

  public void invoke(final InvokeAgentRequest request) {
    final String userText = request.getUserMessage();
    final Message response = agent.invoke(sessionId, Message.user(userText));
    sendEvent(request.getId(), "thoughts", response.getThoughts());
    sendEvent(request.getId(), "finalAnswer", response.getContent());
  }

  public void buildPrompt(final BuildPromptRequest request) {
    final List<Message> messages = agent.buildPrompt(sessionId);
    final List<Map<String, String>> out = new ArrayList<>();
    for (Message message : messages) {
      final Map<String, String> entry = new HashMap<>();
      entry.put("role", message.getRole().name().toLowerCase());
      entry.put("content", message.getContent());
      out.add(entry);
    }
    sendResult(request.getId(), Map.of("messages", out));
  }

  private void sendResult(final String id, final Map<String, Object> result) {
    send(Map.of("id", id, "result", result));
  }

  @SuppressWarnings("unchecked")
  private void sendEvent(final String sessionId, final String event, final Object payload) {
    if (payload == null || (payload instanceof String text && text.isBlank())) {
      return;
    }
    final Map<String, Object> body = new HashMap<>();
    body.put("sessionId", sessionId);
    body.put("event", event);
    if (payload instanceof Map<?, ?> map) {
      body.putAll((Map<String, Object>) map);
    } else {
      body.put("text", payload);
    }
    send(body);
  }

  private void send(final Map<String, Object> payload) {
    try {
      System.out.println(JsonUtils.toJson(payload));
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }
}

