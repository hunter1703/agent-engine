package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.agents.processors.response.PartOrderingResponseProcessor;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor.ResponseProcessingResult;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class PartOrderingResponseProcessorTest {

  @Test
  void enforcesCanonicalOrdering() {
    final PartOrderingResponseProcessor processor = PartOrderingResponseProcessor.INSTANCE;
    final Session session = Session.builder("s1")
        .appName("app")
        .userId("u1")
        .state(new ConcurrentHashMap<>())
        .events(new ArrayList<>())
        .build();
    final InvocationContext context = InvocationContext.builder()
        .session(session)
        .invocationId("inv-1")
        .build();

    // Mixed order input: Tool Response, Text, Tool Call, Thought
    final List<Part> mixedParts = List.of(
        Part.builder().functionResponse(FunctionResponse.builder().name("t1").response(Map.of("r", "1")).build()).build(),
        Part.builder().text("Regular text").build(),
        Part.builder().functionCall(FunctionCall.builder().name("t2").args(Map.of("a", "b")).build()).build(),
        Part.builder().text("My thought").thought(true).build()
    );

    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().role("model").parts(mixedParts).build())
        .build();

    final ResponseProcessingResult result = processor.processResponse(context, response).blockingGet();
    final List<Part> orderedParts = result.updatedResponse().content().get().parts().get();

    // Verify canonical order: Thought (1), Text (2), Tool Call (3), Tool Response (4)
    assertThat(orderedParts).hasSize(4);
    assertThat(orderedParts.get(0).thought().orElse(false)).isTrue();
    assertThat(orderedParts.get(0).text().get()).isEqualTo("My thought");
    
    assertThat(orderedParts.get(1).thought().orElse(false)).isFalse();
    assertThat(orderedParts.get(1).text().get()).isEqualTo("Regular text");
    
    assertThat(orderedParts.get(2).functionCall().isPresent()).isTrue();
    assertThat(orderedParts.get(3).functionResponse().isPresent()).isTrue();
  }

  @Test
  void handlesEmptyOrSinglePart() {
    final PartOrderingResponseProcessor processor = PartOrderingResponseProcessor.INSTANCE;
    final InvocationContext context = InvocationContext.builder().build();

    // Single part
    final LlmResponse singlePartResponse = LlmResponse.builder()
        .content(Content.builder().parts(List.of(Part.builder().text("Solo").build())).build())
        .build();
    final ResponseProcessingResult result1 = processor.processResponse(context, singlePartResponse).blockingGet();
    assertThat(result1.updatedResponse().content().get().parts().get()).hasSize(1);

    // Empty parts
    final LlmResponse emptyResponse = LlmResponse.builder()
        .content(Content.builder().parts(List.of()).build())
        .build();
    final ResponseProcessingResult result2 = processor.processResponse(context, emptyResponse).blockingGet();
    assertThat(result2.updatedResponse().content().get().parts().get()).isEmpty();
  }
}
