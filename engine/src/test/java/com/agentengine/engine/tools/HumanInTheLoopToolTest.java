package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.adk.events.EventActions;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HumanInTheLoopToolTest {

  @Test
  void shouldRequestConfirmationForTextInput() {
    final HumanInTheLoopTool tool = new HumanInTheLoopTool();
    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.toolConfirmation()).thenReturn(Optional.empty());
    when(toolContext.eventActions()).thenReturn(new EventActions());

    final Map<String, Object> result = tool.execute(toolContext, "Need more detail", "TEXT", List.of("A", " B ", "A", " "),
        Map.of("source", "guardrail"));

    assertThat(result).isEmpty();
    final ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
    final ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
    verify(toolContext).requestConfirmation(promptCaptor.capture(), payloadCaptor.capture());
    assertThat(promptCaptor.getValue()).isEqualTo("Need more detail");
    assertThat(payloadCaptor.getValue())
        .isEqualTo(Map.of("kind", "TEXT", "options", List.of("A", "B"), "context", Map.of("source", "guardrail")));
  }

  @Test
  void shouldEchoAnswerOnConfirmedTextReplay() {
    final HumanInTheLoopTool tool = new HumanInTheLoopTool();
    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.toolConfirmation())
        .thenReturn(Optional.of(ToolConfirmation.builder().confirmed(true).payload(Map.of("answer", "Paris")).build()));

    final Map<String, Object> result = tool.execute(toolContext, "Need more detail", "TEXT", List.of(), Map.of());

    assertThat(result).containsEntry("status", "answered").containsEntry("answer", "Paris");
    verify(toolContext, never()).requestConfirmation(anyString(), any());
  }

  @Test
  void shouldReturnCancelledWhenTextReplayIsRejected() {
    final HumanInTheLoopTool tool = new HumanInTheLoopTool();
    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.toolConfirmation()).thenReturn(Optional.of(ToolConfirmation.builder().confirmed(false).build()));

    final Map<String, Object> result = tool.execute(toolContext, "Need more detail", "TEXT", List.of(), Map.of());

    assertThat(result).containsEntry("status", "cancelled");
    verify(toolContext, never()).requestConfirmation(anyString(), any());
  }

  @Test
  void shouldReturnAllowDecisionOnConfirmedReplay() {
    final HumanInTheLoopTool tool = new HumanInTheLoopTool();
    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.toolConfirmation()).thenReturn(Optional.of(ToolConfirmation.builder().confirmed(true).build()));

    final Map<String, Object> result = tool.execute(toolContext, "Allow this?", "DECISION", List.of("x"), Map.of());

    assertThat(result).containsEntry("decision", "ALLOW");
    verify(toolContext, never()).requestConfirmation(anyString(), any());
  }

  @Test
  void shouldRequestConfirmationWhenOptionsIsNull() {
    final HumanInTheLoopTool tool = new HumanInTheLoopTool();
    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.toolConfirmation()).thenReturn(Optional.empty());
    when(toolContext.eventActions()).thenReturn(new EventActions());

    final Map<String, Object> result = tool.execute(toolContext, "What are you looking for?", "TEXT", null, null);

    assertThat(result).isEmpty();
    final ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
    verify(toolContext).requestConfirmation(promptCaptor.capture(), any());
    assertThat(promptCaptor.getValue()).isEqualTo("What are you looking for?");
  }

  @Test
  void shouldSetEndInvocationWhenRequestingConfirmation() {
    final HumanInTheLoopTool tool = new HumanInTheLoopTool();
    final ToolContext toolContext = mock(ToolContext.class);
    final EventActions eventActions = new EventActions();
    when(toolContext.toolConfirmation()).thenReturn(Optional.empty());
    when(toolContext.eventActions()).thenReturn(eventActions);

    tool.execute(toolContext, "What is your query?", "TEXT", null, null);

    assertThat(eventActions.endInvocation()).contains(true);
  }

  @Test
  void shouldReturnDisallowDecisionOnRejectedReplay() {
    final HumanInTheLoopTool tool = new HumanInTheLoopTool();
    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.toolConfirmation()).thenReturn(Optional.of(ToolConfirmation.builder().confirmed(false).build()));

    final Map<String, Object> result = tool.execute(toolContext, "Allow this?", "DECISION", List.of(), Map.of());

    assertThat(result).containsEntry("decision", "DISALLOW");
    verify(toolContext, never()).requestConfirmation(anyString(), any());
  }
}
