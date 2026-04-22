package com.agentengine.runtime.plugins;

import com.agentengine.runtime.utils.EventUtils;
import com.agentengine.runtime.utils.ToolUtils;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.common.JsonUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.plugins.BasePlugin;
import io.reactivex.rxjava3.core.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AddEventMetadataPlugin extends BasePlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddEventMetadataPlugin.class);
    public static final AddEventMetadataPlugin INSTANCE = new AddEventMetadataPlugin();

    public AddEventMetadataPlugin() {
        super("add_event_metadata");
    }

    @Override
    public Maybe<Event> onEventCallback(InvocationContext invocationContext, Event event) {
        if (ToolUtils.isHumanInTheLoopToolEvent(event)) {
            LOGGER.info("marking event : {} as internal", JsonUtils.toJson(event));
            EventUtils.markAsInternal(event);
        }
        EventUtils.addMetadata(
                event, SessionEventUtils.SESSION_ID, invocationContext.session().id());
        return Maybe.empty();
    }
}
