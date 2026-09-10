package com.agentengine.util.common;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@Default
public class DefaultJsonCodec extends JsonCodec {

  @Inject
  public DefaultJsonCodec(final Instance<CodecModuleProvider> providers) {
    super(providers.stream().toList());
  }

  /** For use in tests — bypasses CDI by accepting a pre-built provider list directly. */
  public DefaultJsonCodec(final List<CodecModuleProvider> providers) {
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
    built.registerModule(new ObjectTypingModule());
    return built;
  }
}
