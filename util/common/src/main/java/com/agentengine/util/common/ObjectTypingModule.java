package com.agentengine.util.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator.Validity;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Activates {@link ObjectMapper.DefaultTyping#JAVA_LANG_OBJECT} on any mapper that registers this
 * module. This mode adds a {@code @class} type discriminator exclusively when the declared type of
 * a slot is exactly {@code Object} — the situation that arises for values inside {@code Map<String,
 * Object>} fields such as {@code FunctionCall.args()} during the ADK interrupt flow.
 */
public final class ObjectTypingModule extends SimpleModule {

  public ObjectTypingModule() {
    super(ObjectTypingModule.class.getSimpleName());
  }

  @Override
  public void setupModule(final SetupContext context) {
    super.setupModule(context);
    context
        .<ObjectMapper>getOwner()
        .activateDefaultTyping(
            new PolymorphicTypeValidator.Base() {
              @Override
              public Validity validateSubClassName(
                  final MapperConfig<?> config,
                  final JavaType baseType,
                  final String subClassName) {
                return Validity.ALLOWED;
              }
            },
            ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT,
            JsonTypeInfo.As.PROPERTY);
  }
}
