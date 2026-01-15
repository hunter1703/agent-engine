package com.localagent.agents.shellagent;

import com.localagent.agents.shellagent.tools.ShellCommandTool;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShellCommandToolTest {
    @Test
    void blocksRmCommand() {
        ShellCommandTool tool = new ShellCommandTool(Duration.ofSeconds(1));
        assertThatThrownBy(() -> tool.execute(Map.of("command", "rm -rf /tmp/foo")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Blocked");
    }
}
