package com.agentengine.internal;

import com.agentengine.tenancy.ProvisioningRequest;

public class CustomerProvisioningRequest extends ProvisioningRequest {

  private Integer id;
  private String name;

  public Integer getId() {
    return id;
  }

  public void setId(final Integer id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }
}
