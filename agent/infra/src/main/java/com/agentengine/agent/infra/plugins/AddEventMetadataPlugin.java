package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.utils.EventUtils;
import com.agentengine.util.agents.SessionEventUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.plugins.BasePlugin;
import io.reactivex.rxjava3.core.Maybe;

public final class AddEventMetadataPlugin extends BasePlugin {
    public static final AddEventMetadataPlugin INSTANCE = new AddEventMetadataPlugin();

    public AddEventMetadataPlugin() {
        super("add_event_metadata");
    }

    @Override
    public Maybe<Event> onEventCallback(InvocationContext invocationContext, Event event) {
        EventUtils.addMetadata(
                event, SessionEventUtils.SESSION_ID, invocationContext.session().id());
        return Maybe.empty();
    }
}
