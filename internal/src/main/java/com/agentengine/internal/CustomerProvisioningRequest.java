package com.agentengine.internal;

import com.agentengine.tenancy.beans.Role;
import com.agentengine.tenancy.beans.User;
import com.agentengine.util.agents.beans.config.DefaultModels;
import com.agentengine.util.infra.provisioning.ProvisioningRequest;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class CustomerProvisioningRequest extends ProvisioningRequest {

  private final String id;
  private final String name;
  private final String domain;

  private DefaultModels defaultModels;
  private List<Role> roles;
  private User user;

  @JsonCreator
  public CustomerProvisioningRequest(
      @JsonProperty(value = "id", required = true) final String id,
      @JsonProperty(value = "name", required = true) final String name,
      @JsonProperty(value = "domain", required = true) final String domain) {
    this.id = id;
    this.name = name;
    this.domain = domain;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDomain() {
    return domain;
  }

  public DefaultModels getDefaultModels() {
    return defaultModels;
  }

  public void setDefaultModels(final DefaultModels defaultModels) {
    this.defaultModels = defaultModels;
  }

  public List<Role> getRoles() {
    return roles;
  }

  public void setRoles(final List<Role> roles) {
    this.roles = roles;
  }

  public User getUser() {
    return user;
  }

  public void setUser(final User user) {
    this.user = user;
  }
}
