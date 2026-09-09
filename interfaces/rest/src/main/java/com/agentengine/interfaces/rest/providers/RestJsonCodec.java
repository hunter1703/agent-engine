package com.agentengine.interfaces.rest.providers;

import com.agentengine.util.common.CodecModuleProvider;
import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.JsonUtils;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Singleton;

/**
 * {@link JsonCodec} for the REST layer. Uses a plain mapper with no default typing so that incoming
 * HTTP request bodies from external clients (which carry no {@code @class} tags) deserialize
 * correctly. {@link com.agentengine.util.common.ObjectTypingModule} is deliberately excluded here —
 * it is only needed for the Mongo persistence path ({@link
 * com.agentengine.util.common.DefaultJsonCodec}).
 */
@Singleton
@Typed(RestJsonCodec.class)
public class RestJsonCodec extends JsonCodec {

  public RestJsonCodec(final Instance<CodecModuleProvider> providers) {
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

    built.setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP));
    return built;
  }
}
