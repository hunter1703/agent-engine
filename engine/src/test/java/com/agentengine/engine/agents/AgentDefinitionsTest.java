package com.agentengine.engine.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.ResponseFormatType;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.state.InMemoryStateStore;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentDefinitionsTest {

  @Test
  void tasksExposeToolNames() {
    PlanningAgent planningAgent = new PlanningAgent(new NoopModel());
    assertThat(planningAgent.name()).isEqualTo("tasks");
    assertThat(planningAgent.description()).contains("Task List");
  }

  private static final class NoopModel implements LLMModel {
    private final ContextManager contextManager =
        new BaseContextManager(new InMemoryStateStore(), "system", "protocol", List.of());

    @Override
    public Message generate(final List<Message> messages) {
      return new Message(Role.ASSISTANT, "", null, null);
    }

    @Override
    public ResponseFormatType responseFormat() {
      return ResponseFormatType.TEXT;
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
