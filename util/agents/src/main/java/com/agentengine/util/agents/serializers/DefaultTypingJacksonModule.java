package com.agentengine.util.agents.serializers;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Jackson module giving any {@link ObjectMapper} it registers with default typing for {@code
 * ToolConfirmation} specifically, when it ends up as the value of an {@code Object}-typed slot
 * (ADK's {@code FunctionCall.args()}/{@code ToolConfirmation.payload()}, both {@code Map<String,
 * Object>}) — without {@code @class}, reading it back gives a generic {@code Map} instead of a real
 * {@code ToolConfirmation}.
 *
 * <p>The sole owner of default-typing configuration for any mapper it registers with — Jackson
 * supports only one such configuration per mapper. Deliberately narrow: an earlier version also
 * matched any {@code com.agentengine.*}/AG-UI class by name, which broke unrelated, unambiguous
 * values that happened to share those namespaces (a plain cross-service return type, and an AG-UI
 * value with its own custom serializer incompatible with {@code @class}-wrapping) — every actual
 * reader of the {@code Object}-typed slots this exists for already casts straight to {@code
 * Map<String, Object>}, so widening past {@code ToolConfirmation} isn't backing any real path.
 */
public final class DefaultTypingJacksonModule extends SimpleModule {

  private static final String AUTO_VALUE_TOOL_CONFIRMATION =
      "com.google.adk.events.AutoValue_ToolConfirmation";

  public DefaultTypingJacksonModule() {
    super("DefaultTypingJacksonModule");
  }

  @Override
  public void setupModule(final SetupContext context) {
    super.setupModule(context);
    final PolymorphicTypeValidator ptv =
        BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build();
    final ObjectMapper mapper = context.getOwner();

    // Default typing is needed because ToolConfirmation sometimes ends up as the value of an
    // Object-typed slot (e.g. inside FunctionCall.args()/ToolConfirmation.payload() itself, both
    // Map<String, Object>) — without @class, reading it back gives a generic Map instead of a
    // real ToolConfirmation. Scoped to exactly this: every actual reader of those Object-typed
    // slots elsewhere in the app already casts straight to Map<String, Object> (see
    // FunctionCall.args()/ToolConfirmation.payload() call sites), so a broader match (e.g. "any
    // com.agentengine.* class") isn't backing any real path — it was tried once and broke
    // unrelated, unambiguous values (a plain cross-service return type, and an AG-UI value with
    // its own custom serializer that doesn't cooperate with @class-wrapping).
    final ObjectMapper.DefaultTypeResolverBuilder typer =
        new ObjectMapper.DefaultTypeResolverBuilder(
            ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT, ptv) {
          @Override
          public boolean useForType(final JavaType t) {
            final String name = t.getRawClass().getName();
            if (name.equals(AUTO_VALUE_TOOL_CONFIRMATION)
                || name.equals("com.google.adk.events.ToolConfirmation")) {
              return true;
            }
            // For all other types, follow standard JAVA_LANG_OBJECT rules
            // (only Object.class, interfaces, and abstract classes get @class)
            return super.useForType(t);
          }
        };
    typer.init(JsonTypeInfo.Id.CLASS, null);
    typer.inclusion(JsonTypeInfo.As.PROPERTY);
    typer.typeProperty("@class");
    mapper.setDefaultTyping(typer);
  }
}
