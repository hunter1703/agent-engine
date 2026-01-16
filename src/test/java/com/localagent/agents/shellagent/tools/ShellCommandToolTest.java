package com.localagent.agents.shellagent.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.localagent.engine.beans.config.AgentConfig;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShellCommandToolTest {

    @Test
    void executeRejectsBlankCommands() {
        ShellCommandTool tool = new ShellCommandTool(Duration.ofSeconds(1));

        assertThatThrownBy(() -> tool.execute(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty command");
    }

    @Test
    void executeBlocksRmCommands() {
        ShellCommandTool tool = new ShellCommandTool(Duration.ofSeconds(1));

        assertThatThrownBy(() -> tool.execute(Map.of("command", "rm -rf /")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Blocked");
    }

    @Test
    void executeReturnsOutputForSafeCommand() {
        ShellCommandTool tool = new ShellCommandTool(Duration.ofSeconds(2));

        String output = tool.execute(Map.of("command", "printf 'ok'"));

        assertThat(output).isEqualTo("ok");
    }

    @Test
    void exposesToolMetadata() {
        ShellCommandTool tool = new ShellCommandTool(Duration.ofSeconds(1));

        assertThat(tool.name()).isEqualTo("run_cmd");
        assertThat(tool.description()).contains("Execute ONE shell command");
    }

    @Test
    void providerCreatesToolFromConfig() {
        ShellCommandToolProvider provider = new ShellCommandToolProvider();

        assertThat(provider.agentName()).isEqualTo("shell_agent");
        assertThat(provider.toolName()).isEqualTo("run_cmd");
        assertThat(provider.create(Map.of("timeout_seconds", 5), AgentConfig.empty())).isNotNull();
    }
}
