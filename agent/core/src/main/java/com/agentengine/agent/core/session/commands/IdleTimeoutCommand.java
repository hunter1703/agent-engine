package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.core.session.SessionActor;
import com.agentengine.util.pekko.actor.IdlePassivationInterceptor;

/**
 * Self-scheduled marker used by {@link IdlePassivationInterceptor} to trigger passivation after a
 * period of inactivity. The interceptor recognizes and swallows it before it ever reaches {@link
 * SessionActor}'s command handler — it is not a real command in the session's own vocabulary.
 */
public record IdleTimeoutCommand() implements SessionCommand {}
