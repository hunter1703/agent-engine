package com.agentengine.agent.core.session.commands;

import com.agentengine.agent.core.session.SessionActor;
import com.agentengine.util.pekko.PekkoSerializable;

/** Marker interface for all commands understood by a {@link SessionActor}. */
public interface SessionCommand extends PekkoSerializable {}
