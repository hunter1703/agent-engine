package com.agentengine.cli;

import com.agentengine.client.AgentRequest;
import com.agentengine.client.AgentRequest.RequestType;
import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.beans.config.ConfigLoader;
import com.agentengine.engine.builders.AgentBuilderFactory;
import com.agentengine.engine.events.AgentEvent;
import com.agentengine.engine.events.AgentEventAdapter;
import com.agentengine.engine.events.AgentEventPublisher;
import com.agentengine.engine.message.Message;
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
      final AgentRequest request = JsonUtils.fromJson(line, AgentRequest.class);
      final RequestType requestType = request.getType();
      if (requestType == null) {
        throw new IllegalArgumentException("Missing request type");
      }
      switch (requestType) {
        case INVOKE_AGENT -> invoke(request);
        case BUILD_PROMPT -> buildPrompt(request);
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
    agent.registerListener(new AgentEventAdapter(new StdoutEventPublisher()));
  }

  public void invoke(final AgentRequest request) {
    final String userText = request.getMessage();
    if (userText == null || userText.isBlank()) {
      throw new IllegalArgumentException("Missing message");
    }
    final Message response = agent.invoke(sessionId, Message.user(userText));
    sendEvent(request.getId(), "thoughts", response.getThoughts());
    sendEvent(request.getId(), "finalAnswer", response.getContent());
  }

  public void buildPrompt(final AgentRequest request) {
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
    } else if (payload instanceof String text) {
      body.put("text", payload);
    } else {
      body.put("payload", payload);
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

  private final class StdoutEventPublisher implements AgentEventPublisher {
    @Override
    public void publish(final AgentEvent event) {
      if (event == null || event.event() == null) {
        return;
      }
      sendEvent(event.sessionId(), event.event(), event.payload());
    }
  }
}
