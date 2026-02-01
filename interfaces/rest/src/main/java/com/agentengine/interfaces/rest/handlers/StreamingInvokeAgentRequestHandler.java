package com.agentengine.interfaces.rest.handlers;

import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.AgentRequest.RequestType;
import com.agentengine.interfaces.rest.services.AgentRuntimeManager;
import com.agentengine.interfaces.rest.services.AgentRuntime;
import com.agui.core.event.BaseEvent;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.RunConfig.StreamingMode;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.disposables.Disposable;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class StreamingInvokeAgentRequestHandler extends AbstractAgentRequestHandler<Multi<BaseEvent>> {

  public StreamingInvokeAgentRequestHandler(final AgentRuntimeManager agentManager) {
    super(agentManager);
  }

  @Override
  public RequestType requestType() {
    return RequestType.STREAMING_INVOKE_AGENT;
  }

  @Override
  public Multi<BaseEvent> handle(final AgentRequest request) {
    final AgentRuntime runtime = getOrCreateRuntime(request);
    final String sessionId = ensureSession(runtime, request.getSessionId());
    final String message = request.getMessage();
    final Content messageContent = Content.fromParts(Part.builder().text(message).build());
    final RunConfig runConfig = RunConfig.builder().setStreamingMode(StreamingMode.SSE).build();
    return Multi.createFrom().emitter(emitter -> {
      final AGUIEventEmitter eventEmitter = new AGUIEventEmitter(sessionId, emitter::emit);
      final AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
      emitter.onTermination(() -> {
        final Disposable disposable = subscriptionRef.get();
        if (disposable != null) {
          disposable.dispose();
        }
      });
      subscriptionRef
          .set(runtime.runner().runAsync(AgentRuntimeManager.DEFAULT_USER_ID, sessionId, messageContent, runConfig)
              .subscribe(eventEmitter::onEvent, error -> {
                eventEmitter.onError(error);
                emitter.fail(error);
              }, () -> {
                eventEmitter.onComplete();
                emitter.complete();
              }));
    });
  }
}
