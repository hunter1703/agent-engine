package com.agentengine.engine.api.tools;

/** All tools must have one method named "execute". The method can have any parameters. */
public interface Tool {
  String ALL = "ALL";

  ToolDescriptor descriptor();
}
