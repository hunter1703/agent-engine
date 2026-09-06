package com.agentengine.util.agents.serializers;

import com.agentengine.util.common.CodecModuleProvider;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.inject.Singleton;

@Singleton
public final class DefaultTypingJacksonModuleProvider implements CodecModuleProvider {

  @Override
  public Module getModule(final boolean includeTypeInfo) {
    return includeTypeInfo ? new TypedJacksonModule() : null;
  }

  /**
   * Broader default typing for callers that explicitly ask for it (a heterogeneous collection with
   * no single shared class, where the receiver needs each element's concrete type embedded in the
   * JSON itself to resolve it — see {@code JsonCodec.serialize(Object, boolean)}). Tags any
   * non-final class or interface.
   */
  private static final class TypedJacksonModule extends SimpleModule {

    private TypedJacksonModule() {
      super(TypedJacksonModule.class.getSimpleName());
    }

    @Override
    public void setupModule(final SetupContext context) {
      super.setupModule(context);
      final ObjectMapper mapper = context.getOwner();
      final PolymorphicTypeValidator ptv =
          BasicPolymorphicTypeValidator.builder().allowIfSubType(Object.class).build();
      mapper.activateDefaultTyping(
          ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    }
  }
}
