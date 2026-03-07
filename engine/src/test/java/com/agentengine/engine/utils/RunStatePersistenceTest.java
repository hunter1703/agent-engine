package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.agents.processors.response.RunCleanupResponseProcessor;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.google.adk.agents.InvocationContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class RunStatePersistenceTest {

  @Test
  void rehydratesRunStateFromPersistedMap() {
    final Session session =
        Session.builder("session-1")
            .appName("app")
            .userId("user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    final InvocationContext context = InvocationContext.builder().session(session).build();

    final RunState persisted = new RunState();
    persisted.updatePlan(new Plan("Durable plan", "Goal"));
    persisted.updateLastToolCall("tool(args)");

    session.state().put("run.state", JsonUtils.toMap(persisted));

    final RunState rehydrated = RunStateUtils.getState(context);

    assertThat(rehydrated.plan()).isNotNull();
    assertThat(rehydrated.plan().getTitle()).isEqualTo("Durable plan");
    assertThat(rehydrated.lastToolCall()).isEqualTo("tool(args)");
    assertThat(rehydrated.violations()).isEmpty();
  }

  @Test
  void cleanupClearsTransientFieldsButKeepsPlan() {
    final Session session =
        Session.builder("session-2")
            .appName("app")
            .userId("user")
            .state(new ConcurrentHashMap<>())
            .events(new ArrayList<>())
            .build();
    final InvocationContext context = InvocationContext.builder().session(session).build();

    final RunState runState = RunStateUtils.getState(context);
    runState.updatePlan(new Plan("Persistent plan", "Goal"));
    runState.updateLastToolCall("run_cmd");
    runState.addViolation(Violation.builder("v1").message("msg").correctionMessage("fix").build());
    runState.incrementOffTopicRetries();

    final LlmResponse finishedResponse =
        LlmResponse.builder().partial(false).turnComplete(true).build();

    RunCleanupResponseProcessor.INSTANCE.processResponse(context, finishedResponse).blockingGet();

    final RunState after = RunStateUtils.getState(context);
    assertThat(after.plan()).isNotNull();
    assertThat(after.plan().getTitle()).isEqualTo("Persistent plan");
    assertThat(after.lastToolCall()).isNull();
    assertThat(after.violations()).isEmpty();
    assertThat(after.offTopicRetries()).isZero();
  }
}
