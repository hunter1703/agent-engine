package com.agentengine.engine.client.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "last_n")
@BsonDiscriminator(value = "last_n")
public class LastNContextConfig extends ContextConfig {
  private int keepLast = 24;

  public LastNContextConfig() {
    super(ContextType.LAST_N);
  }

  public int getKeepLast() {
    return keepLast;
  }

  public void setKeepLast(final int keepLast) {
    this.keepLast = keepLast;
  }
}
