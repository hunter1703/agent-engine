package com.agentengine.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.HybridAgent;
import com.agentengine.engine.agents.PlanningAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.MessageStoreMark;
import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.state.InMemoryMessageStore;
import com.agentengine.engine.state.InMemorySessionStore;
import com.agentengine.engine.tools.DefaultToolExecutor;
import com.agentengine.engine.tools.ToolExecutor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridAgentPlanningIntegrationTest {

  @Test
  void markResetRestoresContextMessages() {
    InMemoryMessageStore store = new InMemoryMessageStore();
    BaseContextManager context = new BaseContextManager("reasoning", store, "system", "protocol", List.of());

    context.appendMessage("session", "run", Message.user("hello"));
    MessageStoreMark mark = context.mark("session");
    context.appendMessage("session", "run", Message.system("router"));
    context.reset("session", mark);

    assertThat(store.getMessages("session", "reasoning")).hasSize(1);
  }

  @Test
  void plannerUpdatesActiveItemAndNotifiesReasoner() {
    InMemoryMessageStore store = new InMemoryMessageStore();
    BaseContextManager reasoningContext = new BaseContextManager("reasoning", store, "system", "protocol", List.of());
    BaseContextManager routerContext = new BaseContextManager("router", store, "system", "protocol", List.of());
    BaseContextManager planningContext = new BaseContextManager("planning", store, "system", "protocol", List.of());

    LLMModel reasoningModel = new QueueModel(ResponseFormatType.JSON,
        List.of(Message.assistant("")), reasoningContext);
    LLMModel routerModel = new QueueModel(ResponseFormatType.JSON,
        List.of(Message.assistant("{\"complex\":true}")), routerContext);

    LLMModel planningModel = new QueueModel(ResponseFormatType.TEXT, List.of(
        Message.assistant("- [pending] (id:step-1) first step\n- [pending] (id:step-2) second step"),
        Message.assistant("- [in_progress] (id:step-1) first step updated\n- [pending] (id:step-2) second step")),
        planningContext);
    PlanningAgent planningAgent = new PlanningAgent(planningModel);
    ToolExecutor toolExecutor = new DefaultToolExecutor(List.of(planningAgent));

    HybridAgent agent = new HybridAgent(reasoningModel, routerModel, toolExecutor,
        new InMemorySessionStore(), 1, "test-agent");

    agent.invoke("session", Message.user("do work"), Agent.NO_OP_LISTENER);

    List<Message> messages = store.getMessages("session", "reasoning");
    assertThat(messages).anyMatch(message -> message.getRole() == Role.SYSTEM
        && message.getContent().contains("Active plan item updated"));
    assertThat(messages).anyMatch(message -> message.getRole() == Role.SYSTEM
        && message.getContent().contains("Current plan step: first step updated"));
  }

  private static final class QueueModel implements LLMModel {
    private final Deque<Message> responses;
    private final ResponseFormatType responseFormat;
    private final ContextManager contextManager;

    private QueueModel(final ResponseFormatType type, final List<Message> responses, final ContextManager contextManager) {
      this.responses = new ArrayDeque<>(responses);
      this.responseFormat = type;
      this.contextManager = contextManager;
    }

    @Override
    public Message generate(final List<Message> messages) {
      if (responses.isEmpty()) {
        return Message.assistant("");
      }
      return responses.removeFirst();
    }

    @Override
    public ResponseFormatType responseFormat() {
      return responseFormat;
    }

    @Override
    public boolean thoughtsEnabled() {
      return false;
    }

    @Override
    public String thoughtsStartTag() {
      return "<think>";
    }

    @Override
    public String thoughtsEndTag() {
      return "</think>";
    }

    @Override
    public ContextManager getContextManager() {
      return contextManager;
    }
  }
}
