package com.agentengine.util.common;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
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

  @Override
  protected ObjectMapper buildMapper(final List<CodecModuleProvider> providers) {
    final ObjectMapper built = JsonUtils.copyMapper();
    for (final CodecModuleProvider provider : providers) {
      final Module module = provider.getModule();
      if (module != null) {
        built.registerModule(module);
      }
    }
    built.setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP));
    built.registerModule(new ObjectTypingModule());
    return built;
  }
}
