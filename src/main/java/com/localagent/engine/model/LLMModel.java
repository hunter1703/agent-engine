package com.localagent.engine.model;

import com.localagent.engine.message.Message;
import java.util.List;

public interface LLMModel {

    Message generate(List<Message> messages);

    String format();

    String thoughtsStartTag();

    String thoughtsEndTag();
}
