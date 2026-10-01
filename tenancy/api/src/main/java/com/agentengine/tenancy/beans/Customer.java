package com.agentengine.tenancy.beans;

import com.agentengine.util.common.annotations.Index;
import com.agentengine.util.common.beans.BaseEntity;

@Index(def = "{'domain': 1}", name = "domain_unique", unique = true)
public class Customer extends BaseEntity {

  public static final String FIELD_DOMAIN = "domain";

  private String name;
  private String domain;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDomain() {
    return domain;
  }

  public void setDomain(final String domain) {
    this.domain = domain;
  }
}
