package com.agentengine.plugins.echoagent.tools;

import com.agentengine.engine.tools.AgentTool;
import java.util.Map;

public final class EchoTool implements AgentTool {
  @Override
  public String name() {
    return "echo";
  }

  @Override
  public String description() {
    return "Echoes input text with an optional prefix.";
  }

  @Override
  public String execute(final Map<String, Object> args) {
    Object prefix = args == null ? null : args.get("prefix");
    Object text = args == null ? null : args.get("text");
    return (prefix == null ? "" : prefix.toString()) + (text == null ? "" : text.toString());
  }
}
