package com.localagent.engine.model;

import com.localagent.engine.message.Message;
import dev.langchain4j.model.chat.request.ResponseFormat;

import java.util.List;

public interface LLMModel {

    Message generate(List<Message> messages);

    ResponseFormat responseFormat();

    boolean thoughtsEnabled();

    String thoughtsStartTag();

    String thoughtsEndTag();
}
