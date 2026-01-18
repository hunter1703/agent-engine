package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "mongo")
@BsonDiscriminator(value = "mongo")
public class MongoStateStoreConfig extends StateStoreConfig {
  private String uri;

  private String database;

  private String sessionsCollection;

  private String messagesCollection;

  private String toolExecsCollection;

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
