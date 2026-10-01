package com.agentengine.agent.core.session.state;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.util.context.Context;

/**
 * A message waiting for its turn, with the context that turn runs in: this session, for the user
 * who sent the message, or the session alone for a message no user sent.
 */
public record EnqueuedMessage(UserMessage message, Context context) {}
