package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.ResourceUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.model.ModelServerUtils;
import com.alibaba.fastjson2.TypeReference;
import com.google.adk.models.langchain4j.LangChain4j;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.*;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class OllamaModelBuilder extends LangchainModelBuilder {
  @Override
  public String type() {
    return ModelConfig.Provider.OLLAMA.name().toLowerCase();
  }
}
