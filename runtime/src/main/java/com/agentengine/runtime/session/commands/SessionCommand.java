package com.agentengine.runtime.session.commands;

import com.agentengine.runtime.session.SessionActor;
import com.agentengine.util.pekko.PekkoSerializable;

/** Marker interface for all commands understood by a {@link SessionActor}. */
public interface SessionCommand extends PekkoSerializable {
}
