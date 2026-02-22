package com.agentengine.engine.api.tools;

/**
 * All tools must have one method named "execute" and it should return Map<String, Object>. The
 * method can have any parameters.
 */
public interface Tool {
  String ALL = "ALL";

  ToolDescriptor descriptor();
}
