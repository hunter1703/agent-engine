package com.agentengine.interfaces.rest.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.SessionTitleGenerator;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.builders.model.OpenAIModelBuilder;
import com.agentengine.engine.model.DelegatingLLMModel;
import com.google.adk.events.Event;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionTitleGeneratorTest {

  @Test
  void generateTitleUsesModelResponse() {
    final OpenAIModelBuilder modelBuilder = mock(OpenAIModelBuilder.class);
    final ModelConfig modelConfig = new ModelConfig();
    modelConfig.setModel("qwen2.5-1.5b-instruct-q5_k_m");

    final DelegatingLLMModel model = mock(DelegatingLLMModel.class);
    when(modelBuilder.build(modelConfig)).thenReturn(model);
    final LlmResponse response = LlmResponse.builder()
        .content(Content.builder().role("model").parts(Part.builder().text("\"Title\"").build()).build()).build();
    when(model.generateContent(any(LlmRequest.class), eq(false))).thenReturn(Flowable.just(response));

    final SessionTitleGenerator generator = new SessionTitleGenerator(modelBuilder);
    final Event event = Event.builder().id("event-1").author("user")
        .content(Content.builder().role("user").parts(Part.builder().text("hello").build()).build()).build();

    final Optional<String> title = generator.generateTitle(List.of(event));

    assertThat(title).contains("Title");
  }
}
