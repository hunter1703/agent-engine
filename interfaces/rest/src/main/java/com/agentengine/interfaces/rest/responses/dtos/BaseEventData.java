package com.agentengine.interfaces.rest.responses.dtos;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Base class for all response event data types
 */
public abstract class BaseEventData {
  protected final String type;

  public BaseEventData(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}