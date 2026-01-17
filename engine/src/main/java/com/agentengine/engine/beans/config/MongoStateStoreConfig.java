package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(typeName = "mongo")
public class MongoStateStoreConfig extends StateStoreConfig {
  @JSONField(name = "uri")
  private String uri;

  @JSONField(name = "database")
  private String database;

  @JSONField(name = "sessions_collection")
  private String sessionsCollection;

  @JSONField(name = "messages_collection")
  private String messagesCollection;

  @JSONField(name = "tool_execs_collection")
  private String toolExecsCollection;

  @JSONField(name = "summaries_collection")
  private String summariesCollection;

  public MongoStateStoreConfig() {
    super(StateStoreType.MONGO);
  }

  public String getUri() {
    return uri;
  }

  public void setUri(final String uri) {
    this.uri = uri;
  }

  public String getDatabase() {
    return database;
  }

  public void setDatabase(final String database) {
    this.database = database;
  }

  public String getSessionsCollection() {
    return sessionsCollection;
  }

  public void setSessionsCollection(final String sessionsCollection) {
    this.sessionsCollection = sessionsCollection;
  }

  public String getMessagesCollection() {
    return messagesCollection;
  }

  public void setMessagesCollection(final String messagesCollection) {
    this.messagesCollection = messagesCollection;
  }

  public String getToolExecsCollection() {
    return toolExecsCollection;
  }

  public void setToolExecsCollection(final String toolExecsCollection) {
    this.toolExecsCollection = toolExecsCollection;
  }

  public String getSummariesCollection() {
    return summariesCollection;
  }

  public void setSummariesCollection(final String summariesCollection) {
    this.summariesCollection = summariesCollection;
  }
}
