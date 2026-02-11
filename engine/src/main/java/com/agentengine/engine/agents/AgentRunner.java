package com.agentengine.engine.agents;

import com.agentengine.engine.repository.SessionRepository;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Action;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

import static com.agentengine.engine.agents.AgentSessionRuntimeManager.DEFAULT_USER_ID;
import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_USER_ID;

@Singleton
public class AgentRunner {
  private static final Logger LOG = LoggerFactory.getLogger(AgentRunner.class);
  private final SessionRepository sessionRepository;
  private final SessionTitleGenerator sessionTitleGenerator;

  public AgentRunner(SessionRepository sessionRepository, SessionTitleGenerator sessionTitleGenerator) {
    this.sessionRepository = sessionRepository;
    this.sessionTitleGenerator = sessionTitleGenerator;
  }

  public Flowable<Event> run(final AgentSessionRuntime runtime, String text) {
    final RunConfig runConfig = RunConfig.builder().build();
    final Runner runner = runtime.runner();
    final String sessionId = runtime.sessionId();
    return runner.runAsync(DEFAULT_USER_ID, sessionId, buildFromText(text), runConfig)
        .doOnComplete(updateTitle(runner, sessionId));
  }

  public Flowable<Event> runStreaming(final AgentSessionRuntime runtime, String text) {
    final RunConfig runConfig = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.SSE).build();
    final Runner runner = runtime.runner();
    final String sessionId = runtime.sessionId();
    return runner.runAsync(DEFAULT_USER_ID, sessionId, buildFromText(text), runConfig)
        .doOnComplete(updateTitle(runner, sessionId));
  }

  private Action updateTitle(final Runner runner, final String sessionId) {
    return () -> {
      final Session session = Objects.requireNonNull(runner.sessionService().getSession("APP", DEFAULT_USER_ID, sessionId, Optional.empty()).blockingGet());
      sessionTitleGenerator.generateTitle(session.events()).ifPresent(title -> {
        sessionRepository.updateTitle(sessionId, title);
      });
    };
  }

  private static Content buildFromText(final String text) {
    return Content.fromParts(Part.fromText(text));
  }
}
