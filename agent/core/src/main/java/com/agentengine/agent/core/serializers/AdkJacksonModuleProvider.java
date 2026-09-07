package com.agentengine.agent.core.serializers;

import com.agentengine.util.common.CodecModuleProvider;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.adk.events.ToolConfirmation;
import com.google.genai.types.FinishReason;
import jakarta.inject.Singleton;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ADK/genai Jackson quirks. Lives in {@code agent/core}, not a shared module (e.g. {@code
 * util:agents}), because only {@code agent/core} still deserializes raw ADK {@code Event}s at all:
 * {@code catalog/core} stopped once {@code SessionServiceImpl.hydrateSession}/{@code includeEvents}
 * was removed, and {@code interfaces/rest} never mapped raw ADK types (AG-UI mapping happens
 * entirely in {@code agent}). Keeping this off those other services' classpaths means their {@code
 * includeTypeInfo=false} mapper stays genuinely free of default typing.
 */
@Singleton
public final class AdkJacksonModuleProvider implements CodecModuleProvider {

  @Override
  public Module getModule(final boolean includeTypeInfo) {
    return new AdkJacksonModule(includeTypeInfo);
  }

  /**
   * Public and no-arg-constructible so it can also be loaded outside CDI, by Pekko's own {@code
   * pekko.actor.serialization.jackson.jackson-modules} config (a plain {@code Class.forName(...)
   * .getDeclaredConstructor().newInstance()} on the configured class name — see {@code
   * deploy/configs/local/actor/default.conf}) for the {@code jackson-cbor} serializer that
   * (de)serializes {@code SessionEvent} — including its {@code rawEvent} ADK {@code Event} field —
   * when Pekko delivers it between actors.
   */
  public static final class AdkJacksonModule extends SimpleModule {

    private static final Logger LOG = LoggerFactory.getLogger(AdkJacksonModule.class);
    private static final String AUTO_VALUE_TOOL_CONFIRMATION =
        "com.google.adk.events.AutoValue_ToolConfirmation";

    private final boolean includeTypeInfo;

    public AdkJacksonModule() {
      this(false);
    }

    private AdkJacksonModule(final boolean includeTypeInfo) {
      super(AdkJacksonModule.class.getSimpleName());
      this.includeTypeInfo = includeTypeInfo;
    }

    @Override
    public void setupModule(final SetupContext context) {
      super.setupModule(context);
      final ObjectMapper mapper = context.getOwner();
      // FinishReason has @JsonCreator(String) but Jackson infers properties-based mode instead of
      // delegating because the class has bean-like fields. The mixin forces DELEGATING mode so
      // that the scalar string value (e.g. "STOP") is passed directly to the constructor.
      mapper.addMixIn(FinishReason.class, FinishReasonMixin.class);
      // AutoValue builders wrap Optional fields via Optional.of(), which NPEs on null.
      // Configuring Nulls.SKIP globally on any mapper this module registers with causes Jackson
      // to omit setter calls for null JSON values, leaving Optional fields at their builder
      // default of Optional.empty(). This is safe here: null JSON values are equivalent to absent
      // fields for the immutable ADK/genai value types this module targets.
      mapper.setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP));

      if (includeTypeInfo) {
        // Default typing is already broad here (see DefaultTypingJacksonModuleProvider's
        // NON_FINAL module) — adding the narrow ToolConfirmation typing below too would be
        // redundant, and Jackson only allows one default-typing configuration per mapper.
        return;
      }

      // Default typing for ToolConfirmation specifically, when it ends up as the value of an
      // Object-typed slot (ADK's FunctionCall.args()/ToolConfirmation.payload(), both
      // Map<String, Object>) — without @class, reading it back gives a generic Map instead of a
      // real ToolConfirmation, breaking recovery of a paused session. Deliberately narrow: an
      // earlier version also matched any com.agentengine.*/AG-UI class by name, which broke
      // unrelated, unambiguous values that happened to share those namespaces (a plain
      // cross-service return type, and an AG-UI value with its own custom serializer incompatible
      // with @class-wrapping) — every actual reader of the Object-typed slots this exists for
      // already casts straight to Map<String, Object>, so widening past ToolConfirmation isn't
      // backing any real path.
      final PolymorphicTypeValidator ptv =
          BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build();
      final ObjectMapper.DefaultTypeResolverBuilder typer =
          new ObjectMapper.DefaultTypeResolverBuilder(
              ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, ptv) {
            @Override
            public boolean useForType(final JavaType javaType) {
              final String name = javaType.getRawClass().getName();
              return name.equals(AUTO_VALUE_TOOL_CONFIRMATION)
                  || name.equals("com.google.adk.events.ToolConfirmation");
            }
          };
      typer.init(JsonTypeInfo.Id.CLASS, null);
      typer.inclusion(JsonTypeInfo.As.PROPERTY);
      typer.typeProperty("@class");
      mapper.setDefaultTyping(typer);
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
}
