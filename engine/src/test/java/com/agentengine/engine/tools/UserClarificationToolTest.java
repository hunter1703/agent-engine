package com.agentengine.engine.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UserClarificationToolTest {

  @Test
  void shouldTreatBlankFunctionCallIdAsUnavailableConfirmationContext() {
    final UserClarificationTool tool = new UserClarificationTool();
    final ToolContext toolContext = mock(ToolContext.class);
    when(toolContext.functionCallId()).thenReturn(Optional.of(""));

    final Map<String, Object> result =
        tool.execute(toolContext, "Need more detail", List.of("A", "B"));

    assertThat(result)
        .containsEntry("status", "confirmation_unavailable")
        .containsEntry("clarification", "Need more detail")
        .containsEntry("reason", "user_clarification")
        .containsEntry("options", List.of("A", "B"))
        .containsEntry(
            "error", "Confirmation context is not available for user_clarification tool call.");
    verify(toolContext, never()).requestConfirmation(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
  }
}
