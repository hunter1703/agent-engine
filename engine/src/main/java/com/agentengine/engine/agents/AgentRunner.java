package com.agentengine.engine.agents;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Action;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import java.util.Objects;
import java.util.Optional;

import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_APP;
import static com.agentengine.engine.api.beans.session.AgentSession.DEFAULT_USER_ID;
import static com.google.adk.agents.RunConfig.StreamingMode.SSE;
import static com.google.genai.types.Part.fromText;

@Singleton
public class AgentRunner {
  private static final Logger LOG = LoggerFactory.getLogger(AgentRunner.class);
  private final AgentSessionRepository agentSessionRepository;
  private final SessionTitleGenerator sessionTitleGenerator;

  public AgentRunner(AgentSessionRepository agentSessionRepository, SessionTitleGenerator sessionTitleGenerator) {
    this.agentSessionRepository = agentSessionRepository;
    this.sessionTitleGenerator = sessionTitleGenerator;
  }

  @Timeout(5000)
  @Retry(maxRetries = 2)
  public Flowable<Event> run(final AgentSessionRuntime runtime, String text) {
    LOG.debug("run - session_id={} text=\"{}\"", runtime.sessionId(), StringUtils.substring(text, 0, 50));
    final RunConfig runConfig = RunConfig.builder().setStreamingMode(SSE).build();
    final Runner runner = runtime.runner();
    final String sessionId = runtime.sessionId();
    return runner.runAsync(DEFAULT_USER_ID, sessionId, buildFromText(text), runConfig)
        .doOnNext(
            event -> LOG.debug("run event - session_id={} eventType={}", sessionId, event.getClass().getSimpleName()))
        .doOnComplete(() -> LOG.debug("run complete - session_id={}", sessionId))
        .doOnComplete(updateTitle(runner, sessionId));
  }

  private Action updateTitle(final Runner runner, final String sessionId) {
    return () -> {
      final Session session = Objects.requireNonNull(
          runner.sessionService().getSession(DEFAULT_APP, DEFAULT_USER_ID, sessionId, Optional.empty()).blockingGet());
      sessionTitleGenerator.generateTitle(session.events()).ifPresent(title -> {
        agentSessionRepository.updateTitle(sessionId, title);
      });
    };
  }

  private static Content buildFromText(final String text) {
    return Content.fromParts(fromText(text));
  }
}
