package com.agentengine.engine.model;

import com.agentengine.engine.api.beans.session.Message;
import dev.langchain4j.model.chat.request.ResponseFormat;
import java.util.List;

public interface LLMModel {

  Message generate(List<Message> messages);

  ResponseFormat responseFormat();

  boolean thoughtsEnabled();

  String thoughtsStartTag();

  String thoughtsEndTag();
}
