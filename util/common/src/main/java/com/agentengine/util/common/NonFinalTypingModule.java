package com.agentengine.util.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator.Validity;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Activates {@link ObjectMapper.DefaultTyping#NON_FINAL} typing on any mapper that registers this
 * module. This mode adds a {@code @class} type discriminator for all non-final types, allowing
 * concrete subtypes to survive round-trips through generic slots such as {@code Object[]} — the
 * shape used by the gRPC transport to carry method arguments.
 */
public final class NonFinalTypingModule extends SimpleModule {

  public NonFinalTypingModule() {
    super(NonFinalTypingModule.class.getSimpleName());
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
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY);
  }
}
