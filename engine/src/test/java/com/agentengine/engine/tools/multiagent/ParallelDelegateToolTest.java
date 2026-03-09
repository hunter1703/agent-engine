package com.agentengine.engine.tools.multiagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.services.AgentExecutionService;
import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParallelDelegateToolTest {

  @Test
  void executesSectioningAndConcatenatesOutputs() {
    final AgentExecutionService service =
        request -> Flowable.just(textEvent("out:" + request.getMessage()));
    final ParallelDelegateTool tool = new ParallelDelegateTool(service);

    final Map<String, Object> result =
        tool.execute(
            null,
            "worker",
            List.of("task a", "task b"),
            "SECTIONING",
            null,
            "CONCATENATE",
            "ALL_COMPLETE",
            null);

    assertThat(result).containsEntry("status", "success");
    assertThat(result).containsEntry("requested_branches", 2);
    assertThat(result).containsEntry("completed_branches", 2);
    assertThat(result.get("aggregated_output").toString()).contains("out:task a").contains("out:task b");
  }

  @Test
  void votingModeUsesMajorityVoteAggregation() {
    final AgentExecutionService service =
        request -> {
          final String sessionId = request.getSessionId();
          final String output =
              sessionId != null && sessionId.contains("-p2-") ? "option-b" : "option-a";
          return Flowable.just(textEvent(output));
        };
    final ParallelDelegateTool tool = new ParallelDelegateTool(service);

    final Map<String, Object> result =
        tool.execute(
            null,
            "worker",
            List.of("answer the same question"),
            "VOTING",
            3,
            "MAJORITY_VOTE",
            "ALL_COMPLETE",
            null);

    assertThat(result).containsEntry("status", "success");
    assertThat(result).containsEntry("requested_branches", 3);
    assertThat(result.get("aggregated_output")).isEqualTo("option-a");
  }

  private static Event textEvent(final String text) {
    return Event.builder()
        .id("event-" + text.hashCode())
        .author("model")
        .content(Content.builder().role("model").parts(Part.fromText(text)).build())
        .build();
  }
}
