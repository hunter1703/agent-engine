package com.agentengine.runtime.serializers;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.adk.events.ToolConfirmation;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jackson module for Pekko CBOR serialization compatibility.
 */
public final class PekkoJacksonModule extends SimpleModule {

    private static final Logger LOG = LoggerFactory.getLogger(PekkoJacksonModule.class);
    private static final String AUTO_VALUE_TOOL_CONFIRMATION = "com.google.adk.events.AutoValue_ToolConfirmation";

    public PekkoJacksonModule() {
        super("PekkoJacksonModule");
    }

    @Override
    public void setupModule(final SetupContext context) {
        super.setupModule(context);
        final PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(Object.class)
                .build();
        final ObjectMapper mapper = context.getOwner();
        mapper.activateDefaultTypingAsProperty(ptv, ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, "@class");
        // AutoValue builders wrap Optional fields via Optional.of(), which NPEs on null.
        // Configuring Nulls.SKIP globally on this mapper causes Jackson to omit setter calls
        // for null JSON values, leaving Optional fields at their builder default of
        // Optional.empty(). This is safe on the dedicated Pekko CBOR mapper: null JSON values
        // are equivalent to absent fields for the immutable value types it serializes.
        mapper.setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP));
        // When ToolConfirmation is stored in an Object-typed field, CBOR default typing embeds
        // "com.google.adk.events.AutoValue_ToolConfirmation" as the @class value. Jackson must
        // be able to deserialize that concrete class, but its constructor is private. Register
        // the same deserializer for the AutoValue implementation class so recovery works.
        registerAutoValueToolConfirmationDeserializer(mapper);
    }

    @SuppressWarnings("unchecked")
    private static void registerAutoValueToolConfirmationDeserializer(final ObjectMapper mapper) {
        try {
            final Class<ToolConfirmation> autoValueClass =
                    (Class<ToolConfirmation>) Class.forName(AUTO_VALUE_TOOL_CONFIRMATION);
            mapper.registerModule(
                    new SimpleModule().addDeserializer(autoValueClass, new ToolConfirmationDeserializer()));
        } catch (final ClassNotFoundException exception) {
            LOG.warn(
                    "AutoValue_ToolConfirmation not found on classpath; CBOR recovery may fail for paused sessions",
                    exception);
        }
    }

    /**
     * Deserializes {@code ToolConfirmation} and its AutoValue implementation via the public {@code
     * ToolConfirmation.builder()} factory, bypassing private constructors.
     */
    private static final class ToolConfirmationDeserializer extends StdDeserializer<ToolConfirmation> {

        private ToolConfirmationDeserializer() {
            super(ToolConfirmation.class);
        }

        @Override
        public ToolConfirmation deserialize(
                final JsonParser jsonParser, final DeserializationContext deserializationContext) throws IOException {
            final JsonNode node = jsonParser.readValueAsTree();
            final ToolConfirmation.Builder builder = ToolConfirmation.builder();
            if (node.has("hint")) {
                builder.hint(node.get("hint").asText(""));
            }
            if (node.has("confirmed")) {
                builder.confirmed(node.get("confirmed").asBoolean(false));
            }
            final JsonNode payloadNode = node.get("payload");
            if (payloadNode != null && !payloadNode.isNull()) {
                builder.payload(jsonParser.getCodec().treeToValue(payloadNode, Object.class));
            }
            return builder.build();
        }
    }
}
