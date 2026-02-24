package com.agentengine.engine.tools.shell;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.Map;

class ShellCommandToolTest {

  @Test
  void testComplexCommand() {
    ShellCommandTool tool = new ShellCommandTool();
    // Test pipe and redirect and logic operator
    Map<String, Object> result = tool.execute("echo 'a' && echo 'b'");
    
    assertNotNull(result);
    String output = result.get("output").toString().trim();
    assertEquals("a\nb", output, "Actual output was: [" + output + "]");
  }

  @Test
  void testBlockedCommand() {
    ShellCommandTool tool = new ShellCommandTool();
    // assertThrows(IllegalArgumentException.class, () -> tool.execute("rm -rf /"));
    assertThrows(IllegalArgumentException.class, () -> tool.execute("echo 'hi' ; rm file"));
  }
}
