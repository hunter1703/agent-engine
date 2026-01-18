package com.agentengine.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.message.Message;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbstractAgentEngineTest {

  @Test
  void invokeListenersNotifiesAllRegisteredListeners() {
    TestEngine engine = new TestEngine();
    List<String> called = new ArrayList<>();
    engine.registerListener("s1", new StartListener(called));
    engine.registerListener("s2", new EndListener(called));

    engine.trigger("s1");

    assertThat(called).containsExactly("start-s1", "end-s1");
  }

  private static final class TestEngine extends AbstractAgentEngine {
    private void trigger(final String sessionId) {
      invokeListeners(listener -> listener.onReasoningStart(sessionId));
      invokeListeners(listener -> listener.onReasoningEnd(sessionId, Message.assistant("done")));
    }

    @Override
    public Message invoke(final String sessionId, final Message message) {
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
    public void onReasoningStart(final String sessionId) {
      called.add(STR."start-\{sessionId}");
    }

    @Override
    public void onFinalAnswer(final String sessionId, final Message message) {
    }
  }

  private static final class EndListener implements AgentListener {
    private final List<String> called;

    private EndListener(final List<String> called) {
      this.called = called;
    }

    @Override
    public void onReasoningEnd(final String sessionId, final Message message) {
      called.add(STR."end-\{sessionId}");
    }

    @Override
    public void onFinalAnswer(final String sessionId, final Message message) {
    }
  }
}
