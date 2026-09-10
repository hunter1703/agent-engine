package com.agentengine.interfaces.rest.providers;

import com.agentengine.util.common.CodecModuleProvider;
import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.JsonUtils;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * {@link JsonCodec} for the REST layer. No default typing: every polymorphic REST DTO already uses
 * closed {@code @JsonTypeInfo(Id.NAME)} + {@code @JsonSubTypes}, and responses are terminal (never
 * deserialized back into Java). Don't add {@link com.agentengine.util.common.ObjectTypingModule}
 * here — its validator allows every class, which is only safe for internal wire formats (gRPC,
 * Pekko, Mongo). A REST DTO that needs {@code @class} polymorphism should get its own module with a
 * restricted allow-list validator.
 */
@Singleton
@Typed(RestJsonCodec.class)
public class RestJsonCodec extends JsonCodec {

  @Inject
  public RestJsonCodec(final Instance<CodecModuleProvider> providers) {
    super(providers.stream().toList());
  }

  /** For use in tests — bypasses CDI by accepting a pre-built provider list directly. */
  public RestJsonCodec(final List<CodecModuleProvider> providers) {
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
