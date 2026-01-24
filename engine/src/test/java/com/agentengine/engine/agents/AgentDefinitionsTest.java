package com.agentengine.engine.agents;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Role;
import com.agentengine.engine.context.BaseContextManager;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.state.InMemoryStateStore;
import com.agentengine.engine.api.StateStore;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentDefinitionsTest {

  @Test
  void tasksExposeToolNames() {
    StateStore stateStore = new InMemoryStateStore();
    LLMModel model = new NoopModel();
    BaseContextManager contextBuilder = new BaseContextManager(stateStore, "system", "protocol", List.of());

    PlanningAgent planningAgent = new PlanningAgent(model, contextBuilder, stateStore);
    assertThat(planningAgent.name()).isEqualTo("tasks");
    assertThat(planningAgent.description()).contains("Task List");
  }

  private static final class NoopModel implements LLMModel {
    @Override
    public Message generate(final List<Message> messages) {
      return new Message(Role.ASSISTANT, "", null, null);
    }

    @Override
    public ResponseFormat responseFormat() {
      return new ResponseFormat.Builder().type(ResponseFormatType.TEXT).build();
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
  }
}
