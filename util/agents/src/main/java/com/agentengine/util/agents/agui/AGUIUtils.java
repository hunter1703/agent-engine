package com.agentengine.util.agents.agui;

import com.agentengine.util.agents.beans.ConfirmationKind;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.beans.FileDetails;
import com.agui.core.types.CustomEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlinx.serialization.json.JsonArray;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonLiteral;
import kotlinx.serialization.json.JsonNull;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonPrimitive;

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
                "parentMessageId", str(parentMessageId),
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
        fields.put("confirmationId", str(confirmationId));
        fields.put("prompt", nullable(prompt));
        fields.put("originalToolCallId", nullable(originalToolCallId));
        fields.put(
                "options",
                options == null
                        ? JsonNull.INSTANCE
                        : new JsonArray(options.stream()
                                .map(option -> (JsonElement) str(option))
                                .toList()));
        fields.put("kind", kind == null ? JsonNull.INSTANCE : str(kind.name()));
        return new CustomEvent("confirmation_requested", new JsonObject(fields), timestamp, null);
    }

    /** Emitted when a user responds to a confirmation request. */
    public static CustomEvent buildConfirmedEvent(
            final String confirmationId, final boolean confirmed, final String answer, final long timestamp) {
        final Map<String, JsonElement> fields = Map.of(
                "confirmationId", str(confirmationId),
                "confirmed", bool(confirmed),
                "answer", nullable(answer));
        return new CustomEvent("confirmed", new JsonObject(fields), timestamp, null);
    }

    /** Emitted when a guardrail violation is detected and corrected. */
    public static CustomEvent buildCorrectionEvent(final Violation violation, final long timestamp) {
        return new CustomEvent(
                "correction", new JsonObject(Map.of("message", str(violation.message()))), timestamp, null);
    }

    // --- JSON helpers ---

    private static JsonPrimitive str(final String value) {
        return new JsonLiteral(value, true, null);
    }

    private static JsonElement nullable(final String value) {
        return value != null ? str(value) : JsonNull.INSTANCE;
    }

    private static JsonElement bool(final boolean value) {
        return new JsonLiteral(value, false, null);
    }

    private static JsonElement num(final long value) {
        return new JsonLiteral(value, false, null);
    }

    private static JsonObject fileDetailsJson(final FileDetails fileDetails) {
        if (fileDetails == null) {
            return new JsonObject(Map.of());
        }
        final LinkedHashMap<String, JsonElement> fields = new LinkedHashMap<>();
        fields.put("name", nullable(fileDetails.name()));
        fields.put("source", nullable(fileDetails.source()));
        fields.put("type", fileDetails.type() != null ? str(fileDetails.type().name()) : JsonNull.INSTANCE);
        fields.put("mimeType", nullable(fileDetails.mimeType()));
        fields.put("size", num(fileDetails.size()));
        return new JsonObject(fields);
    }
}
