package com.agentengine.interfaces.rest.dto.responses;

import java.util.List;

public record ModelList(String object, List<Model> data) {
  public ModelList(List<Model> data) {
    this("list", data);
  }
}
