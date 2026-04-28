package com.agentengine.agent.infra.tools.shell;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.google.adk.tools.ToolConfirmation;
import com.google.adk.tools.ToolContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShellCommandToolTest {

    private final ShellCommandTool tool = new ShellCommandTool();

    @Test
    void shouldUseMediumRiskLevelWhenCommandSpecificConfirmationHandlesDangerousCommands() {
        assertThat(ShellCommandTool.DESCRIPTOR.riskLevel()).isEqualTo(ToolRiskLevel.MEDIUM);
    }

    @Test
    void shouldPauseForConfirmationWhenCommandMatchesBlockedPattern() {
        final ToolContext toolContext =
                ToolContext.builder(null).functionCallId("call-1").build();

        final Map<String, Object> response = tool.execute(toolContext, "rm -rf tmp");

        assertThat(response).isEmpty();
        assertThat(toolContext.actions().endInvocation()).contains(true);
        assertThat(toolContext.actions().requestedToolConfirmations()).containsKey("call-1");
        final ToolConfirmation confirmation =
                toolContext.actions().requestedToolConfirmations().get("call-1");
        assertThat(confirmation.hint()).isEqualTo("Should the command `rm -rf tmp` be executed?");
        assertThat(confirmation.payload()).isEqualTo(Map.of("command", "rm -rf tmp"));
    }

    @Test
    void shouldRejectCommandWhenConfirmationIsDenied() {
        final ToolContext toolContext = ToolContext.builder(null)
                .functionCallId("call-1")
                .toolConfirmation(ToolConfirmation.builder().confirmed(false).build())
                .build();

        final Map<String, Object> response = tool.execute(toolContext, "rm -rf tmp");

        assertThat(response).containsEntry("error", "This command is rejected.");
        assertThat(toolContext.actions().requestedToolConfirmations()).isEmpty();
        assertThat(toolContext.actions().endInvocation()).isEmpty();
    }

    @Test
    void shouldExecuteCommandWhenBlockedPatternIsConfirmed() {
        final ToolContext toolContext = ToolContext.builder(null)
                .functionCallId("call-1")
                .toolConfirmation(ToolConfirmation.builder().confirmed(true).build())
                .build();

        final Map<String, Object> response = tool.execute(toolContext, "rm missing-file");

        assertThat(response).containsKey("output");
        assertThat(response.get("output")).asString().startsWith("(exit=");
        assertThat(toolContext.actions().requestedToolConfirmations()).isEmpty();
        assertThat(toolContext.actions().endInvocation()).isEmpty();
    }

    @Test
    void shouldExecuteCommandWhenCommandDoesNotMatchBlockedPattern() {
        final Map<String, Object> response = tool.execute(null, "printf 'hello'");

        assertThat(response).containsEntry("output", "hello");
    }
}
