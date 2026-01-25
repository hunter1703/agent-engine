package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.utils.StringUtils;
import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeKey = "type", seeAlso = {LastNContextManagerConfig.class})
@BsonDiscriminator(key = "type")
public abstract class ContextManagerConfig implements Config {
  private String type;
  private String systemPrompt;
  private MessageStoreConfig messageStore = new InMemoryMessageStoreConfig();

  protected ContextManagerConfig(final ContextType contextType) {
    this.type = contextType.name().toLowerCase();
  }

  @Override
  public void validate() {
    if (StringUtils.isBlank(systemPrompt)) {
      throw new IllegalArgumentException("systemPrompt is required");
    }
    messageStore.validate();
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(final String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public MessageStoreConfig getMessageStore() {
    return messageStore;
  }

  public void setMessageStore(final MessageStoreConfig messageStore) {
    this.messageStore = messageStore;
  }



  public enum ContextType {
    SUMMARIZE, LAST_N
  }
}
