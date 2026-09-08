package com.agentengine.util.ms.client;

import com.agentengine.util.common.CodecModuleProvider;
import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.JsonUtils;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Singleton;

/**
 * {@link JsonCodec} for the gRPC transport layer. Activates {@link
 * ObjectMapper.DefaultTyping#NON_FINAL} typing so that non-final types passed as method args
 * (serialized as {@code Object[]}) carry a {@code @class} tag, allowing the server to reconstruct
 * the correct concrete type via {@link GRPCServerImpl#deserializeArgs}. Final types (e.g. {@code
 * String}, {@code Integer}) are never tagged since they have no subtypes.
 */
@Singleton
@Typed(GrpcJsonCodec.class)
public class GrpcJsonCodec extends JsonCodec {

  public GrpcJsonCodec(final Instance<CodecModuleProvider> providers) {
    super(providers);
  }

  @Override
  protected ObjectMapper buildMapper(final Instance<CodecModuleProvider> providers) {
    final ObjectMapper built = JsonUtils.copyMapper();
    for (final CodecModuleProvider provider : providers) {
      final Module module = provider.getModule();
      if (module != null) {
        built.registerModule(module);
      }
    }
    built.registerModule(new GrpcTypingModule());
    return built;
  }

  private static final class GrpcTypingModule extends SimpleModule {

    private GrpcTypingModule() {
      super(GrpcTypingModule.class.getSimpleName());
    }

    @Override
    public void setupModule(final SetupContext context) {
      super.setupModule(context);
      context
          .<ObjectMapper>getOwner()
          .activateDefaultTyping(
              new PolymorphicTypeValidator.Base() {},
              ObjectMapper.DefaultTyping.NON_FINAL,
              JsonTypeInfo.As.PROPERTY);
    }
  }
}
