package com.agentengine.util.pekko;

import com.agentengine.util.common.CodecModuleProvider;
import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.ObjectTypingModule;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import org.apache.pekko.serialization.jackson.JacksonObjectMapperFactory;

/** Backs Pekko's Jackson mapper with {@link PekkoJsonCodec}'s mapper instead of a bespoke one. */
@Singleton
public final class PekkoJsonCodecFactory extends JacksonObjectMapperFactory {

  private final PekkoJsonCodec pekkoJsonCodec;

  @Inject
  public PekkoJsonCodecFactory(final PekkoJsonCodec pekkoJsonCodec) {
    this.pekkoJsonCodec = pekkoJsonCodec;
  }

  @Override
  public ObjectMapper newObjectMapper(final String bindingName, final JsonFactory jsonFactory) {
    return pekkoJsonCodec.buildMapper(jsonFactory);
  }

  /** {@link JsonCodec} for Pekko cluster messages/persistence (both ends are our own services). */
  @Singleton
  @Typed(PekkoJsonCodec.class)
  public static class PekkoJsonCodec extends JsonCodec {

    private final List<CodecModuleProvider> providers;

    @Inject
    public PekkoJsonCodec(final Instance<CodecModuleProvider> providers) {
      this(providers.stream().toList());
    }

    /** For use in tests — bypasses CDI by accepting a pre-built provider list directly. */
    public PekkoJsonCodec(final List<CodecModuleProvider> providers) {
      super(providers);
      this.providers = providers;
    }

    @Override
    protected ObjectMapper buildMapper(final List<CodecModuleProvider> providers) {
      // Called from the JsonCodec superclass constructor, before this.providers is assigned —
      // must use the parameter, not the field.
      return buildMapper(providers, JsonUtils.copyMapper());
    }

    /**
     * Rebuilds this codec's mapper on the given {@link JsonFactory} (e.g. Pekko's CBOR factory)
     * instead of the default JSON one — from scratch via {@link JsonUtils#copyMapper(JsonFactory)},
     * not by copying the already-built mapper: {@link ObjectMapper#copyWith} can't do that reliably
     * since {@code JsonMapper} (what every codec's mapper is built from) overrides {@code copy()}
     * but not {@code copyWith()}, so it always fails that method's same-exact-class check.
     */
    public ObjectMapper buildMapper(final JsonFactory jsonFactory) {
      return buildMapper(providers, JsonUtils.copyMapper(jsonFactory));
    }

    private static ObjectMapper buildMapper(
        final List<CodecModuleProvider> providers, final ObjectMapper base) {
      for (final CodecModuleProvider provider : providers) {
        final Module module = provider.getModule();
        if (module != null) {
          base.registerModule(module);
        }
      }
      base.registerModule(new ObjectTypingModule());
      return base;
    }
  }
}
