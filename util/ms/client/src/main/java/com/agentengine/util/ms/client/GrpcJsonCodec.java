package com.agentengine.util.ms.client;

import com.agentengine.util.common.CodecModuleProvider;
import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.NonFinalTypingModule;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * {@link JsonCodec} for the gRPC transport layer. Activates {@link
 * ObjectMapper.DefaultTyping#NON_FINAL} typing so that non-final types passed as method args
 * (serialized as {@code Object[]}) carry a {@code @class} tag, allowing the server to reconstruct
 * the correct concrete type via {@code GRPCServerImpl.deserializeArgs}. Final types (e.g. {@code
 * String}, {@code Integer}) are never tagged since they have no subtypes.
 */
@Singleton
@Typed(GrpcJsonCodec.class)
public class GrpcJsonCodec extends JsonCodec {

  @Inject
  public GrpcJsonCodec(final Instance<CodecModuleProvider> providers) {
    super(providers.stream().toList());
  }

  /** For use in tests — bypasses CDI by accepting a pre-built mapper directly. */
  public GrpcJsonCodec(final List<CodecModuleProvider> providers) {
    super(providers);
  }

  @Override
  protected ObjectMapper buildMapper(final List<CodecModuleProvider> providers) {
    final ObjectMapper built = JsonUtils.copyMapper();
    for (final CodecModuleProvider provider : providers) {
      final Module module = provider.getModule();
      if (module != null) {
        built.registerModule(module);
      }
    }
    built.registerModule(new NonFinalTypingModule());
    return built;
  }
}
