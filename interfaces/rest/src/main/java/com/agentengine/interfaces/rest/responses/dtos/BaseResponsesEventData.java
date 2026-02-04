package com.agentengine.interfaces.rest.responses.dtos;

/**
 * Base class for all response event data types
 */
public abstract class BaseResponsesEventData {
  protected final String type;

  public BaseResponsesEventData(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }
}