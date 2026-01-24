package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "last_n")
@BsonDiscriminator(value = "last_n")
public class LastNContextManagerConfig extends ContextManagerConfig {
  private int keepLast = 24;

  public LastNContextManagerConfig() {
    super(ContextType.LAST_N);
  }

  public int getKeepLast() {
    return keepLast;
  }

  public void setKeepLast(final int keepLast) {
    this.keepLast = keepLast;
  }
}
