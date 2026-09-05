package com.agentengine.util.agents.beans;

import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.mongodb.mongo.MongoUtils;
import com.google.adk.events.Event;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

/**
 * Hand-written {@link Codec} for {@link SessionEvent}, the highest-volume document type in the
 * system — a session's full event history is fetched in bulk on every stream/replay request.
 *
 * <p>Registered (as a CDI bean — see {@code MongoClientFactory}) ahead of the automatic POJO codec
 * fallback so it replaces reflection-based decoding for this one type. Under load, profiling
 * (jstack sampling during a sustained h2load run against a large session) showed threads
 * consistently inside {@code PojoCodecImpl.decodeProperties}, which resolves each BSON field
 * against the class's property list via a linear {@code Stream.findFirst()} scan — O(fields ×
 * properties) per document. Reading/writing each field directly here removes that scan entirely;
 * field order and names must stay in sync with {@link SessionEvent}'s own properties. A benchmark
 * comparing this against delegating non-{@code rawEvent} fields to the automatic codec confirmed
 * the automatic codec costs ~42% more CPU at this event volume — see the {@code
 * project-mongo-codec-benchmark-results} memory.
 *
 * <p>Owns converting {@link SessionEvent#getRawEvent()} to/from the {@code rawEventJson} BSON field
 * itself, via the injected {@link JsonCodec} — {@link SessionEvent} is a plain bean, not a CDI
 * component, so it can't inject a codec itself, and this class already is one.
 */
@Singleton
public final class SessionEventCodec implements Codec<SessionEvent> {

  private static final String FIELD_RAW_EVENT_JSON = "rawEventJson";

  private final JsonCodec jsonCodec;

  @Inject
  public SessionEventCodec(final JsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
  }

  @Override
  public void encode(
      final BsonWriter writer, final SessionEvent value, final EncoderContext encoderContext) {
    writer.writeStartDocument();
    writeString(writer, MongoUtils.FIELD_MONGO_ID, value.getId());
    writer.writeInt64(BaseEntity.FIELD_CREATED_TIME, value.getCreatedTime());
    writer.writeInt64(BaseEntity.FIELD_UPDATED_TIME, value.getUpdatedTime());
    writer.writeInt64(BaseEntity.FIELD_VERSION, value.getVersion());
    writeString(writer, SessionEvent.FIELD_ROOT_SESSION_ID, value.getRootSessionId());
    writeString(writer, SessionEvent.FIELD_PARENT_SESSION_ID, value.getParentSessionId());
    writeString(writer, SessionEvent.FIELD_SESSION_ID, value.getSessionId());
    writer.writeInt64(SessionEvent.FIELD_SEQUENCE, value.getSequence());
    writeString(
        writer, SessionEvent.FIELD_TYPE, value.getType() == null ? null : value.getType().name());
    writeString(writer, SessionEvent.FIELD_TURN_ID, value.getTurnId());
    writeString(writer, FIELD_RAW_EVENT_JSON, jsonCodec.serialize(value.getRawEvent()));
    writeString(writer, SessionEvent.FIELD_ROLLBACK_ID, value.getRollbackId());
    writer.writeEndDocument();
  }

  @Override
  public SessionEvent decode(final BsonReader reader, final DecoderContext decoderContext) {
    final SessionEvent event = new SessionEvent();
    reader.readStartDocument();
    while (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
      final String fieldName = reader.readName();
      switch (fieldName) {
        case MongoUtils.FIELD_MONGO_ID -> event.setId(readNullableString(reader));
        case BaseEntity.FIELD_CREATED_TIME -> event.setCreatedTime(reader.readInt64());
        case BaseEntity.FIELD_UPDATED_TIME -> event.setUpdatedTime(reader.readInt64());
        case BaseEntity.FIELD_VERSION -> event.setVersion(reader.readInt64());
        case SessionEvent.FIELD_ROOT_SESSION_ID ->
            event.setRootSessionId(readNullableString(reader));
        case SessionEvent.FIELD_PARENT_SESSION_ID ->
            event.setParentSessionId(readNullableString(reader));
        case SessionEvent.FIELD_SESSION_ID -> event.setSessionId(readNullableString(reader));
        case SessionEvent.FIELD_SEQUENCE -> event.setSequence(reader.readInt64());
        case SessionEvent.FIELD_TYPE ->
            event.setType(SessionEvent.Type.valueOfOrDefault(readNullableString(reader)));
        case SessionEvent.FIELD_TURN_ID -> event.setTurnId(readNullableString(reader));
        case FIELD_RAW_EVENT_JSON ->
            event.setRawEvent(jsonCodec.deserialize(readNullableString(reader), Event.class));
        case SessionEvent.FIELD_ROLLBACK_ID -> event.setRollbackId(readNullableString(reader));
        default -> reader.skipValue();
      }
    }
    reader.readEndDocument();
    return event;
  }

  @Override
  public Class<SessionEvent> getEncoderClass() {
    return SessionEvent.class;
  }

  private static void writeString(final BsonWriter writer, final String name, final String value) {
    if (value == null) {
      writer.writeNull(name);
    } else {
      writer.writeString(name, value);
    }
  }

  private static String readNullableString(final BsonReader reader) {
    if (reader.getCurrentBsonType() == BsonType.NULL) {
      reader.readNull();
      return null;
    }
    return reader.readString();
  }
}
