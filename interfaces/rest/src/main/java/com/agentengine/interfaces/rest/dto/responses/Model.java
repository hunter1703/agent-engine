package com.agentengine.interfaces.rest.dto.responses;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Model object. */
public record Model(
    String id, String object, long created, @JsonProperty("owned_by") String ownedBy) {
  public Model {
    if (object == null) object = "model";
  }
}
