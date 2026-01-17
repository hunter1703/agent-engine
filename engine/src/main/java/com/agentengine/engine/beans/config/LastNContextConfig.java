package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(typeName = "last_n")
public class LastNContextConfig extends ContextConfig {
  @JSONField(name = "keep_last")
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
