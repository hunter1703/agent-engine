package com.agentengine.agent.core.serializers;

import com.agentengine.util.common.CodecModuleProvider;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.genai.types.FinishReason;
import jakarta.inject.Singleton;

/**
 * ADK/genai Jackson quirks. Lives in {@code agent/core}, not a shared module (e.g. {@code
 * util:agents}), because only {@code agent/core} still deserializes raw ADK {@code Event}s at all:
 * {@code catalog/core} stopped once {@code SessionServiceImpl.hydrateSession}/{@code includeEvents}
 * was removed, and {@code interfaces/rest} never mapped raw ADK types (AG-UI mapping happens
 * entirely in {@code agent}). Keeping this off those other services' classpaths means their mapper
 * stays genuinely free of default typing.
 */
@Singleton
public final class AdkJacksonModuleProvider implements CodecModuleProvider {

  @Override
  public Module getModule() {
    return new AdkJacksonModule();
  }

  /**
   * Public and no-arg-constructible so it can also be loaded outside CDI, by Pekko's own {@code
   * pekko.actor.serialization.jackson.jackson-modules} config (a plain {@code Class.forName(...)
   * .getDeclaredConstructor().newInstance()} on the configured class name — see {@code
   * deploy/configs/local/actor/default.conf}) for the {@code jackson-cbor} serializer that
   * (de)serializes {@code SessionEvent} — including its {@code rawEvent} ADK {@code Event} field —
   * when Pekko delivers it between actors.
   */
  public static final class AdkJacksonModule extends SimpleModule {

    public AdkJacksonModule() {
      super(AdkJacksonModule.class.getSimpleName());
    }

    @Override
    public void setupModule(final SetupContext context) {
      super.setupModule(context);
      final ObjectMapper mapper = context.getOwner();
      // FinishReason has @JsonCreator(String) but Jackson infers properties-based mode instead of
      // delegating because the class has bean-like fields. The mixin forces DELEGATING mode so
      // that the scalar string value (e.g. "STOP") is passed directly to the constructor.
      mapper.addMixIn(FinishReason.class, FinishReasonMixin.class);
      // AutoValue builders wrap Optional fields via Optional.of(), which NPEs on null.
      // Configuring Nulls.SKIP causes Jackson to omit setter calls for null JSON values, leaving
      // Optional fields at their builder default of Optional.empty().
      mapper.setDefaultSetterInfo(JsonSetter.Value.forValueNulls(Nulls.SKIP));
    }

    private abstract static class FinishReasonMixin {
      @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
      public FinishReasonMixin(final String value) {}
    }
  }
}
