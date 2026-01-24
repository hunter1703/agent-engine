package com.agentengine.engine.api.beans.config;

import com.agentengine.engine.api.utils.StringUtils;
import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeKey = "type", seeAlso = {LastNContextManagerConfig.class})
@BsonDiscriminator(key = "type")
public abstract class ContextManagerConfig implements Config {
  private String type;
  private String systemPrompt;
  private StateStoreConfig stateStore = new MemoryStateStoreConfig();

  protected ContextManagerConfig(final ContextType contextType) {
    this.type = contextType.name().toLowerCase();
  }

  @Override
  public void validate() {
    if (StringUtils.isBlank(systemPrompt)) {
      throw new IllegalArgumentException("systemPrompt is required");
    }
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


  public StateStoreConfig getStateStore() {
    return stateStore;
  }

  public void setStateStore(final StateStoreConfig stateStore) {
    this.stateStore = stateStore;
  }

  public enum ContextType {
    SUMMARIZE, LAST_N
  }
}
