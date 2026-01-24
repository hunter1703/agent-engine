package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.session.Message;

import java.util.List;

public interface LLMModel {

  Message generate(List<Message> messages);

  ResponseFormatType responseFormat();

  boolean thoughtsEnabled();

  String thoughtsStartTag();

  String thoughtsEndTag();

  ContextManager getContextManager();
}
