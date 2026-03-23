package com.agentengine.runtime.actor;

import com.agentengine.util.agents.beans.CorrectionMetadata;
import com.agentengine.util.common.CollectionUtils;

import java.util.Map;

public final class SessionEventUtils {

    public static final String CORRECTION = "correction";
    public static final String CORRECTION_TYPE = "correction_type";
    public static final String CORRECTION_CODE = "correction_code";
    public static final String CORRECTION_MESSAGE = "correction_message";
    public static final String INTERNAL = "internal";

    private SessionEventUtils(){}

    public static boolean isCorrectionEvent(final SessionEvent event) {
        final Map<String, Object> metadata = CollectionUtils.nullSafeMap(event == null ? null : event.metadata());
        return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(metadata, CORRECTION));
    }

    public static CorrectionMetadata extractCorrectionMetadata(final SessionEvent event) {
        if (!isCorrectionEvent(event)) {
            return null;
        }
        final Map<String, Object> metadata = CollectionUtils.nullSafeMap(event.metadata());
        final String type = CollectionUtils.getStringValueFromMap(metadata, CORRECTION);
        final String code = CollectionUtils.getStringValueFromMap(metadata, CORRECTION_TYPE);
        final String message = CollectionUtils.getStringValueFromMap(metadata, CORRECTION_MESSAGE);
        return new CorrectionMetadata(type, code, message);
    }

    public static boolean isInternal(final SessionEvent event) {
        final Map<String, Object> metadata = CollectionUtils.nullSafeMap(event == null ? null : event.metadata());
        return Boolean.TRUE.equals(CollectionUtils.getBooleanValueFromMap(metadata, INTERNAL));
    }
}
