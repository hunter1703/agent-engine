package com.agentengine.engine.model;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.utils.FinalAnswerAndToolCorrection;
import com.agentengine.engine.utils.Parser;
import com.google.adk.flows.llmflows.RequestProcessor;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.langchain4j.LangChain4j;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;

public class LangChain4JLLMModel extends LangChain4j {
    private final Parser parser;
    private final String protocol;

    public LangChain4JLLMModel(ChatModel chatModel, Parser parser,  String protocol) {
        super(chatModel);
        this.parser = parser;
        this.protocol = protocol;
    }

    public String getProtocol() {
        return protocol;
    }

    public List<RequestProcessor> getRequestProcessors() {
        return List.of(FinalAnswerAndToolCorrection.builder().convertToThought(true).build(), parser);
    }

    public List<ResponseProcessor> getResponseProcessors() {
        return List.of(parser, FinalAnswerAndToolCorrection.builder().convertToThought(true).build());
    }
}
