package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.agents.processors.Parser;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DelegatingLLMModelTest {

  @Test
  void shouldDropPayloadLessPartsBeforeDelegatingToModel() {
    final BaseLlm delegate = mock(BaseLlm.class);
    when(delegate.model()).thenReturn("mock-model");
    when(delegate.generateContent(any(), eq(false)))
        .thenReturn(Flowable.just(LlmResponse.builder().build()));

    final LlmRequest request =
        LlmRequest.builder()
            .contents(
                List.of(
                    Content.builder()
                        .role("user")
                        .parts(List.of(Part.builder().build(), Part.fromText("hello")))
                        .build()))
            .build();

    final DelegatingLLMModel model =
        new DelegatingLLMModel(
            delegate,
            Parser.builder().toolCallingEnabled(true).parseToolCallsFromText(true).build(),
            "protocol",
            true,
            true);

    model.generateContent(request, false).blockingFirst();

    final ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(delegate).generateContent(captor.capture(), eq(false));
    final LlmRequest sent = captor.getValue();
    assertThat(sent.contents()).hasSize(1);
    assertThat(sent.contents().getFirst().parts().orElseThrow()).hasSize(1);
    assertThat(sent.contents().getFirst().parts().orElseThrow().getFirst().text())
        .contains("hello");
  }

  @Test
  void shouldPropagateKnownLangChain4jNullTextBugWithoutFallback() {
    final BaseLlm delegate = mock(BaseLlm.class);
    when(delegate.model()).thenReturn("mock-model");

    final NullPointerException npe = new NullPointerException();
    npe.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement(
              "com.google.adk.models.langchain4j.LangChain4j", "toParts", "LangChain4j.java", 533)
        });
    when(delegate.generateContent(any(), eq(false))).thenReturn(Flowable.error(npe));

    final DelegatingLLMModel model =
        new DelegatingLLMModel(
            delegate,
            Parser.builder().toolCallingEnabled(true).parseToolCallsFromText(true).build(),
            "protocol",
            true,
            true);

    assertThatThrownBy(() -> model.generateContent(LlmRequest.builder().build(), false).blockingFirst())
        .isInstanceOf(NullPointerException.class);
  }
}
