package com.agentengine.engine.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.session.SessionContextSummary;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SummarizingContextManagerTest {

  @Test
  void keepsOriginalContextWhenBelowTrigger() {
    final SessionContextSummaryService summaryService = mock(SessionContextSummaryService.class);
    final BaseLlm summaryModel = mock(BaseLlm.class);
    final SummarizingContextManager contextManager =
        new SummarizingContextManager(100, 20, "summary-model", summaryModel, summaryService);
    final List<Content> contents =
        List.of(Content.builder().role("user").parts(Part.fromText("small context")).build());

    final List<Content> prompt = contextManager.buildPrompt("agent-a", "session-a", contents);

    assertThat(prompt).isEqualTo(contents);
    verify(summaryService, never()).upsert(any(), any(), any(), anyInt(), any(), anyInt(), any());
  }

  @Test
  void compactsOlderContextAndKeepsRecentWindow() {
    final SessionContextSummaryService summaryService = mock(SessionContextSummaryService.class);
    when(summaryService.get("session-a")).thenReturn(Optional.empty());

    final BaseLlm summaryModel = new StaticSummaryModel("Compacted summary");
    final SummarizingContextManager contextManager =
        new SummarizingContextManager(32, 10, "summary-model", summaryModel, summaryService);
    final List<Content> contents =
        List.of(
            Content.builder()
                .role("user")
                .parts(Part.fromText("one two three four five six seven eight nine ten"))
                .build(),
            Content.builder()
                .role("model")
                .parts(Part.fromText("alpha beta gamma delta epsilon zeta eta theta iota kappa"))
                .build(),
            Content.builder()
                .role("user")
                .parts(Part.fromText("eleven twelve thirteen fourteen fifteen sixteen seventeen"))
                .build(),
            Content.builder()
                .role("model")
                .parts(Part.fromText("omega sigma rho lambda mu nu xi omicron pi"))
                .build());

    final List<Content> prompt = contextManager.buildPrompt("agent-a", "session-a", contents);

    assertThat(prompt).hasSize(2);
    assertThat(prompt.getFirst().text()).contains("Compacted summary");
    assertThat(prompt.get(1).text()).isEqualTo("omega sigma rho lambda mu nu xi omicron pi");
    verify(summaryService)
        .upsert(
            eq("session-a"),
            eq("agent-a"),
            eq("Compacted summary"),
            eq(3),
            any(),
            anyInt(),
            eq("summary-model"));
  }

  @Test
  void usesStoredSummaryCutoffAndIgnoresRawHistoryBeforeCutoff() {
    final SessionContextSummaryService summaryService = mock(SessionContextSummaryService.class);
    final SessionContextSummary storedSummary = new SessionContextSummary();
    storedSummary.setSummaryText("Previously summarized context");
    storedSummary.setSummarizedUntilContentCount(2);
    when(summaryService.get("session-a")).thenReturn(Optional.of(storedSummary));

    final BaseLlm summaryModel = mock(BaseLlm.class);
    final SummarizingContextManager contextManager =
        new SummarizingContextManager(200, 30, "summary-model", summaryModel, summaryService);
    final List<Content> contents =
        List.of(
            Content.builder().role("user").parts(Part.fromText("older-1")).build(),
            Content.builder().role("model").parts(Part.fromText("older-2")).build(),
            Content.builder().role("user").parts(Part.fromText("recent-1")).build(),
            Content.builder().role("model").parts(Part.fromText("recent-2")).build());

    final List<Content> prompt = contextManager.buildPrompt("agent-a", "session-a", contents);

    assertThat(prompt).hasSize(3);
    assertThat(prompt.getFirst().text()).contains("Previously summarized context");
    assertThat(prompt.get(1).text()).isEqualTo("recent-1");
    assertThat(prompt.get(2).text()).isEqualTo("recent-2");
    verify(summaryService, never()).upsert(any(), any(), any(), anyInt(), any(), anyInt(), any());
  }

  private static final class StaticSummaryModel extends BaseLlm {
    private final String summary;

    private StaticSummaryModel(final String summary) {
      super("summary-model");
      this.summary = summary;
    }

    @Override
    public Flowable<LlmResponse> generateContent(final LlmRequest llmRequest, final boolean stream) {
      final Content content = Content.builder().role("model").parts(Part.fromText(summary)).build();
      return Flowable.just(LlmResponse.builder().content(content).build());
    }

    @Override
    public BaseLlmConnection connect(final LlmRequest llmRequest) {
      return null;
    }
  }
}
