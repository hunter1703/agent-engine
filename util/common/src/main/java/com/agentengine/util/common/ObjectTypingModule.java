package com.agentengine.util.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    final ObjectMapper mapper = context.getOwner();
    mapper.activateDefaultTyping(
        AllowAllPolymorphicTypeValidator.INSTANCE,
        ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT,
        JsonTypeInfo.As.PROPERTY);
  }
}
