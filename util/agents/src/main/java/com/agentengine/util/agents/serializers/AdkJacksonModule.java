package com.agentengine.util.agents.serializers;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.adk.events.ToolConfirmation;
import com.google.genai.types.FinishReason;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AdkJacksonModule extends SimpleModule {

  private static final Logger LOG = LoggerFactory.getLogger(AdkJacksonModule.class);
  private static final String AUTO_VALUE_TOOL_CONFIRMATION =
      "com.google.adk.events.AutoValue_ToolConfirmation";

  public AdkJacksonModule() {
    super("AdkJacksonModule");
  }

  @Override
  public void setupModule(final SetupContext context) {
    super.setupModule(context);
    final ObjectMapper mapper = context.getOwner();
    // FinishReason has @JsonCreator(String) but Jackson infers properties-based mode instead of
    // delegating because the class has bean-like fields. The mixin forces DELEGATING mode so that
    // the scalar string value (e.g. "STOP") is passed directly to the constructor.
    mapper.addMixIn(FinishReason.class, FinishReasonMixin.class);
    // AutoValue builders wrap Optional fields via Optional.of(), which NPEs on null.
    // Configuring Nulls.SKIP globally on any mapper this module registers with causes Jackson to
    // omit setter calls for null JSON values, leaving Optional fields at their builder default of
    // Optional.empty(). This is safe here: null JSON values are equivalent to absent fields for
    // the immutable ADK/genai value types this module targets.
    mapper.setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP));
    registerAutoValueToolConfirmationDeserializer(mapper);
  }

  private abstract static class FinishReasonMixin {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public FinishReasonMixin(final String value) {}
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
          "AutoValue_ToolConfirmation not found on classpath; recovery may fail for paused"
              + " sessions",
          exception);
    }
  }

  /**
   * Deserializes {@code ToolConfirmation} and its AutoValue implementation via the public {@code
   * ToolConfirmation.builder()} factory, bypassing private constructors.
   */
  private static final class ToolConfirmationDeserializer
      extends StdDeserializer<ToolConfirmation> {

    private ToolConfirmationDeserializer() {
      super(ToolConfirmation.class);
    }

    @Override
    public ToolConfirmation deserialize(
        final JsonParser jsonParser, final DeserializationContext deserializationContext)
        throws IOException {
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
