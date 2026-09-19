package com.agentengine.tenancy;

import com.agentengine.util.common.beans.BaseEntity;

public class Customer extends BaseEntity {

  private String name;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }
}
