package com.agentengine.agent.core.serializers;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.DefaultJsonCodec;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.FunctionCall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the exact shape ADK's own {@code Functions.generateRequestConfirmationEvent} builds: a
 * synthetic {@code "adk_request_confirmation"} {@link FunctionCall} whose {@code args()} is an
 * {@code ImmutableMap} holding the original {@link FunctionCall} (e.g. {@code spawn_agent}) as a
 * plain {@code Map<String, Object>} value under {@code "originalFunctionCall"}.
 *
 * <p>Uses the real {@link DefaultJsonCodec} — the same class {@code SessionEventCodec} persists
 * {@code SessionEvent#rawEvent} through — with {@link AdkJacksonModuleProvider} supplied the way
 * CDI would, so a regression here means a regression in the actual Mongo persistence codec.
 */
class AdkJacksonModuleFunctionCallInMapTest {

  @Test
  void originalFunctionCallSurvivesRoundTripInsideConfirmationArgs() {
    final DefaultJsonCodec codec = new DefaultJsonCodec(List.of(new AdkJacksonModuleProvider()));

    final FunctionCall originalCall =
        FunctionCall.builder()
            .name("spawn_agent")
            .id("call_original_1")
            .args(Map.of("agent_id", "story_phase_1_theme"))
            .build();

    // Exactly what ADK's Functions.generateRequestConfirmationEvent builds:
    // FunctionCall.builder().name("adk_request_confirmation")
    //     .args(ImmutableMap.of("originalFunctionCall", originalCall, "toolConfirmation", ...))
    //     .id(...).build();
    final Map<String, Object> confirmationArgs =
        ImmutableMap.of(
            "originalFunctionCall",
            originalCall,
            "toolConfirmation",
            "placeholder-not-under-test-here");

    final FunctionCall confirmationCall =
        FunctionCall.builder()
            .name("adk_request_confirmation")
            .id("call_confirmation_1")
            .args(confirmationArgs)
            .build();

    final String json = codec.serialize(confirmationCall);
    final FunctionCall roundTripped = codec.deserialize(json, FunctionCall.class);
    final Object roundTrippedOriginal =
        roundTripped.args().orElseThrow().get("originalFunctionCall");

    assertThat(roundTrippedOriginal)
        .as(
            "originalFunctionCall must come back as a real FunctionCall -- both ADK's own"
                + " resume/replay logic and AGUIToolCallMapper.mapInterruptCall/mapResumedResponse"
                + " call FunctionCall methods on it directly, not Map methods")
        .isInstanceOf(FunctionCall.class);

    final FunctionCall roundTrippedOriginalCall = (FunctionCall) roundTrippedOriginal;
    assertThat(roundTrippedOriginalCall.name()).contains("spawn_agent");
    assertThat(roundTrippedOriginalCall.id()).contains("call_original_1");
    assertThat(roundTrippedOriginalCall.args()).contains(Map.of("agent_id", "story_phase_1_theme"));
  }
}
