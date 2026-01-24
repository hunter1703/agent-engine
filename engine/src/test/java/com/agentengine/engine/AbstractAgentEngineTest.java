package com.agentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.AgentListener;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractAgentTest {

  @Test
  void invokeListenersNotifiesAllRegisteredListeners() {
    TestEngine engine = new TestEngine();
    List<String> called = new ArrayList<>();
    engine.registerListener("s1", new StartListener(called));
    engine.registerListener("s2", new EndListener(called));

    engine.trigger("s1");

    assertThat(called).containsExactly("start-s1", "end-s1");
  }

  private static final class TestEngine implements Agent {
    private final List<AgentListener> listeners = new ArrayList<>();

    public void registerListener(String sessionId, AgentListener listener) {
      listeners.add(listener);
    }

    private void trigger(final String sessionId) {
      for (AgentListener listener : listeners) {
        listener.onStepStarted(sessionId, "test-step");
        listener.onStepFinished(sessionId, "test-step");
      }
    }

    @Override
    public Message invoke(final String sessionId, final Message message, AgentListener listener) {
      return null;
    }

    @Override
    public List<Message> buildPrompt(final String sessionId) {
      return List.of();
    }
  }

  private static final class StartListener implements AgentListener {
    private final List<String> called;

    private StartListener(final List<String> called) {
      this.called = called;
    }

    @Override
    public void onStepStarted(final String sessionId, final String stepName) {
      called.add(STR."start-\{sessionId}");
    }
  }

  private static final class EndListener implements AgentListener {
    private final List<String> called;

    private EndListener(final List<String> called) {
      this.called = called;
    }

    @Override
    public void onStepFinished(final String sessionId, final String stepName) {
      called.add(STR."end-\{sessionId}");
    }
  }
}
