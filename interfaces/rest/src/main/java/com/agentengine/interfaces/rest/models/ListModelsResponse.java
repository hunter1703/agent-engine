package com.agentengine.interfaces.rest.models;

import java.util.List;

/**
 * Response class for the /v1/models endpoint
 */
public class ListModelsResponse {

  private String object = "list";
  private List<ModelInfo> data;

  public ListModelsResponse() {
  }

  public ListModelsResponse(List<ModelInfo> data) {
    this.data = data;
  }

  // Getters and setters
  public String getObject() {
    return object;
  }

  public void setObject(String object) {
    this.object = object;
  }

  public List<ModelInfo> getData() {
    return data;
  }

  public void setData(List<ModelInfo> data) {
    this.data = data;
  }
}