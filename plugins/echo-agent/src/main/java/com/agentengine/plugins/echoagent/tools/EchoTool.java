package com.agentengine.plugins.echoagent.tools;

import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.Tool;
import java.util.Map;

public final class EchoTool implements Tool {
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

  @Override
  public Map<String, Object> parametersSchema() {
    //noinspection unchecked
    return JsonUtils.fromJson("""
            {
                "type": "object",
                "properties":
                {
                    "text":
                    {
                        "type": "string",
                        "description": "The text to echo"
                    },
                    "prefix":
                    {
                        "type": "string",
                        "description": "An prefix to prepend to the text. There must be at least 3 characters"
                    }
                },
                "required":
                [
                    "text",
                    "prefix"
                ]
            }
            """, Map.class);
  }
}
