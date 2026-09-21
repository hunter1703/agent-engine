package com.agentengine.internal;

import com.agentengine.tenancy.ProvisioningRequest;
import com.agentengine.util.agents.beans.config.DefaultModels;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CustomerProvisioningRequest extends ProvisioningRequest {

  private final int id;
  private final String name;
  private DefaultModels defaultModels;

  @JsonCreator
  public CustomerProvisioningRequest(
      @JsonProperty(value = "id", required = true) final int id,
      @JsonProperty(value = "name", required = true) final String name) {
    this.id = id;
    this.name = name;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public DefaultModels getDefaultModels() {
    return defaultModels;
  }

  public void setDefaultModels(final DefaultModels defaultModels) {
    this.defaultModels = defaultModels;
  }
}
