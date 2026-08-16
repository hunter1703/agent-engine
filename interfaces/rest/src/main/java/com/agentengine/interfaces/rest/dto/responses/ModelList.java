package com.agentengine.interfaces.rest.dto.responses;

import java.util.List;

/** List of models response. */
public record ModelList(String object, List<Model> data) {
  public ModelList(List<Model> data) {
    this("list", data);
  }
}
