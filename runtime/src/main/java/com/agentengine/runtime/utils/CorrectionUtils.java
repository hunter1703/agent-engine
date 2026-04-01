package com.agentengine.runtime.utils;

import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.State;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class CorrectionUtils {
    public static final String CORRECTION_KEY = State.TEMP_PREFIX + SessionEventUtils.CORRECTION;
    public static final String CORRECTION_TYPE_KEY = State.TEMP_PREFIX + SessionEventUtils.CORRECTION_TYPE;
    public static final String CORRECTION_CODE_KEY = State.TEMP_PREFIX + SessionEventUtils.CORRECTION_CODE;
    public static final String CORRECTION_MESSAGE_KEY = State.TEMP_PREFIX + SessionEventUtils.CORRECTION_MESSAGE;

    private CorrectionUtils() {}

    public static EventActions buildCorrectionActions(final String type, final String code, final String message) {
        final ConcurrentHashMap<String, Object> stateDelta = new ConcurrentHashMap<>();
        stateDelta.put(CORRECTION_KEY, true);
        if (StringUtils.isNotBlank(type)) {
            stateDelta.put(CORRECTION_TYPE_KEY, type);
        }
        if (StringUtils.isNotBlank(code)) {
            stateDelta.put(CORRECTION_CODE_KEY, code);
        }
        if (StringUtils.isNotBlank(message)) {
            stateDelta.put(CORRECTION_MESSAGE_KEY, message);
        }
        return EventActions.builder().stateDelta(stateDelta).build();
    }

    public static Event buildCorrectiveEvent(final InvocationContext context, final String code, final String message) {
        final Content correctiveContent = Content.builder()
                .role("user")
                .parts(List.of(Part.fromText(message)))
                .build();

        return Event.builder()
                .id(Event.generateEventId())
                .invocationId(context.invocationId())
                .author("user")
                .branch(context.branch())
                .actions(buildCorrectionActions("violation", code, message))
                .content(correctiveContent)
                .build();
    }
}
