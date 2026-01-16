package com.localagent.cli;

import com.localagent.cli.beans.BuildPromptRequest;
import com.localagent.cli.beans.InvokeAgentRequest;
import com.localagent.cli.beans.Request;
import com.localagent.engine.AgentEngine;
import com.localagent.engine.builders.AgentBuilderFactory;
import com.localagent.engine.AgentListener;
import com.localagent.engine.beans.config.ConfigLoader;
import com.localagent.engine.message.Message;
import com.localagent.engine.message.ToolCall;
import com.localagent.engine.beans.ToolExecution;
import com.localagent.engine.utils.JsonUtils;
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
    public StdioAgentServer(AgentBuilderFactory agentBuilderFactory, ConfigLoader configLoader) {
        this.agentBuilderFactory = agentBuilderFactory;
        this.configLoader = configLoader;
    }

    public static void main(String[] args) {
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
                default -> throw new IllegalArgumentException(STR."Unsupported request type: \{request.getType()}");
            }
        }

        return 0;
    }

    private void init(final String agentName, final String agentConfig) {
        agent = agentBuilderFactory.getBuilder(agentName).build(agentName, configLoader.loadConfig(Paths.get(agentConfig)));
        sessionId = UUID.randomUUID().toString();
        agent.registerListener(new AgentListener() {
            @Override
            public void onToolPlan(final String sessionId, final List<ToolCall> toolCalls) {
                for (ToolCall call : toolCalls) {
                    sendEvent(sessionId, "tool_plan", Map.of("tool_name", call.name(), "tool_args", call.args()));
                }
            }

            @Override
            public void onToolExecution(final String sessionId, final ToolExecution toolExecution) {
                sendEvent(sessionId, "tool_result", Map.of(
                        "tool_name", toolExecution.name(),
                        "tool_output", toolExecution.output(),
                        "tool_status", toolExecution.status(),
                        "tool_duration_ms", toolExecution.durationMs()
                ));
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

    public void invoke(InvokeAgentRequest request) {
        String userText = request.getUserMessage();
        Message response = agent.invoke(sessionId, Message.user(userText));
        sendEvent(request.getId(), "thoughts", response.getThoughts());
        sendEvent(request.getId(), "finalAnswer", response.getContent());
    }

    public void buildPrompt(BuildPromptRequest request) {
        List<Message> messages = agent.buildPrompt(sessionId);
        List<Map<String, String>> out = new ArrayList<>();
        for (Message message : messages) {
            Map<String, String> entry = new HashMap<>();
            entry.put("role", message.getRole().name().toLowerCase());
            entry.put("content", message.getContent());
            out.add(entry);
        }
        sendResult(request.getId(), Map.of("messages", out));
    }

    private void sendResult(String id, Map<String, Object> result) {
        send(Map.of("id", id, "result", result));
    }

    @SuppressWarnings("unchecked")
    private void sendEvent(String sessionId, String event, Object payload) {
        if (payload == null || (payload instanceof String text && text.isBlank())) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("event", event);
        if (payload instanceof Map<?, ?> map) {
            body.putAll((Map<String, Object>) map);
        } else {
            body.put("text", payload);
        }
        send(body);
    }

    private void send(Map<String, Object> payload) {
        try {
            System.out.println(JsonUtils.toJson(payload));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
