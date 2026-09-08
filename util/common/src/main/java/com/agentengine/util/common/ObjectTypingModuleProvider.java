package com.agentengine.util.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.inject.Singleton;

@Singleton
public final class ObjectTypingModuleProvider implements CodecModuleProvider {

  @Override
  public Module getModule() {
    return new ObjectTypingModule();
  }

  /**
   * Activates {@link ObjectMapper.DefaultTyping#JAVA_LANG_OBJECT} on any mapper that registers this
   * module. This mode adds a {@code @class} type discriminator exclusively when the declared type
   * of a slot is exactly {@code Object}. The validator uses {@code allowIfBaseType(Object)} which
   * permits any subtype resolution when the base type being resolved is {@code Object} — matching
   * precisely the slots this typing mode targets.
   */
  public static class ObjectTypingModule extends SimpleModule {

    public ObjectTypingModule() {
      super(ObjectTypingModule.class.getSimpleName());
    }

    @Override
    public void setupModule(final SetupContext context) {
      super.setupModule(context);
      final ObjectMapper mapper = context.getOwner();
      mapper.activateDefaultTyping(
          new PolymorphicTypeValidator.Base() {},
          ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT,
          JsonTypeInfo.As.PROPERTY);
    }
  }
}
