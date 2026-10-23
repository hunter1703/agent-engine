package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.SessionUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Remembers, as the last plugin to see a model request, the request as the model call sends it. A
 * summary of the history can then be asked by sending that request again with one more message, so
 * the provider's cache of the conversation applies to all of it.
 */
public final class RequestPlugin extends BasePlugin {

  public RequestPlugin() {
    super("request_plugin");
  }

  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder requestBuilder) {
    SessionUtils.getSessionState(callbackContext.invocationContext())
        .runState()
        .rememberRequest(requestBuilder.build());
    return Maybe.empty();
  }
}
