package com.agentengine.util.common;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * A plain {@link JsonCodec} that builds a clean {@link ObjectMapper} from the classpath's
 * registered {@link CodecModuleProvider}s, without injecting any aggressive polymorphic typing
 * modules like {@link ObjectTypingModule}. Use this for external payloads (e.g. connectors, REST
 * APIs) where the schema is known or {@code @class} is unwanted.
 */
@Singleton
@Typed(SimpleJsonCodec.class)
public class SimpleJsonCodec extends JsonCodec {

  @Inject
  public SimpleJsonCodec(final Instance<CodecModuleProvider> providers) {
    super(providers.stream().toList());
  }

  /** For use in tests — bypasses CDI by accepting a pre-built provider list directly. */
  public SimpleJsonCodec(final List<CodecModuleProvider> providers) {
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
    return built;
  }
}
