package com.agentengine.runtime.plugins;

import com.agentengine.runtime.context.ContextManager;
import com.agentengine.runtime.utils.ContentUtils;
import com.agentengine.runtime.utils.EventUtils;
import com.agentengine.util.agents.SessionEventUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.genai.types.Content;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AddEventMetadataPlugin extends BasePlugin {
    public static final AddEventMetadataPlugin INSTANCE = new AddEventMetadataPlugin();

    public AddEventMetadataPlugin() {
        super("add_event_metadata");
    }

    @Override
    public Maybe<Event> onEventCallback(InvocationContext invocationContext, Event event) {
        EventUtils.addMetadata(event, SessionEventUtils.SESSION_ID, invocationContext.session().id());
        return Maybe.empty();
    }

}
