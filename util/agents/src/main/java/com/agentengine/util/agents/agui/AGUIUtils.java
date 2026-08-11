package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.beans.ConfirmationKind;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.FileDetails;
import com.agui.community.core.event.CustomEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonNull;
import kotlinx.serialization.json.JsonObject;

/**
 * Factory for custom AG-UI events that don't have a standard event type in the AG-UI spec.
 *
 * <p>All methods return {@link CustomEvent} instances (which are valid {@code BaseEvent}
 * subclasses) constructed with the event-specific name and a {@link JsonObject} payload.
 */
public final class AGUIUtils {

    private AGUIUtils() {}

    /**
     * Emitted when a message part contains an attachment (e.g. an image produced by the agent).
     * Emitted after any text chunks and before the {@code TextMessageEndEvent} of the parent
     * message, so consumers can associate the attachment with the correct message.
     */
    public static CustomEvent buildAttachmentEvent(
            final String parentMessageId, final FileDetails fileDetails, final long timestamp) {
        final Map<String, JsonElement> fields = Map.of(
                "parentMessageId", JsonUtils.strVal(parentMessageId),
                "fileDetails", fileDetailsJson(fileDetails));
        return new CustomEvent("attachment", new JsonObject(fields), timestamp, null);
    }

    /**
     * Emitted when a session pauses to request human confirmation. {@code confirmationId} is the
     * ID the client must echo back via the confirm endpoint.
     */
    public static CustomEvent buildConfirmationRequestedEvent(
            final String confirmationId,
            final String prompt,
            final String originalToolCallId,
            final List<String> options,
            final ConfirmationKind kind,
            final long timestamp) {
        final LinkedHashMap<String, JsonElement> fields = new LinkedHashMap<>();
        fields.put("confirmationId", JsonUtils.strVal(confirmationId));
        fields.put("prompt", JsonUtils.nullableVal(prompt));
        fields.put("originalToolCallId", JsonUtils.nullableVal(originalToolCallId));
        fields.put(
                "options",
                options == null
                        ? JsonNull.INSTANCE
                        : new JsonArray(options.stream()
                                .map(option -> (JsonElement) JsonUtils.strVal(option))
                                .toList()));
        fields.put("kind", kind == null ? JsonNull.INSTANCE : JsonUtils.strVal(kind.name()));
        return new CustomEvent("confirmation_requested", new JsonObject(fields), timestamp, null);
    }

    /** Emitted when a user responds to a confirmation request. */
    public static CustomEvent buildConfirmedEvent(
            final String confirmationId, final boolean confirmed, final String answer, final long timestamp) {
        final Map<String, JsonElement> fields = Map.of(
                "confirmationId", JsonUtils.strVal(confirmationId),
                "confirmed", JsonUtils.boolVal(confirmed),
                "answer", JsonUtils.nullableVal(answer));
        return new CustomEvent("confirmed", new JsonObject(fields), timestamp, null);
    }

    /** Emitted when a guardrail violation is detected and corrected. */
    public static CustomEvent buildCorrectionEvent(final Violation violation, final long timestamp) {
        return new CustomEvent(
                "correction",
                new JsonObject(Map.of("message", JsonUtils.strVal(violation.message()))),
                timestamp,
                null);
    }

    private static JsonObject fileDetailsJson(final FileDetails fileDetails) {
        if (fileDetails == null) {
            return new JsonObject(Map.of());
        }
        final LinkedHashMap<String, JsonElement> fields = new LinkedHashMap<>();
        fields.put("name", JsonUtils.nullableVal(fileDetails.name()));
        fields.put("source", JsonUtils.nullableVal(fileDetails.source()));
        fields.put(
                "type",
                fileDetails.type() != null ? JsonUtils.strVal(fileDetails.type().name()) : JsonNull.INSTANCE);
        fields.put("mimeType", JsonUtils.nullableVal(fileDetails.mimeType()));
        fields.put("size", JsonUtils.numVal(fileDetails.size()));
        return new JsonObject(fields);
    }
}
