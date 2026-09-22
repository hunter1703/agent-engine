package com.agentengine.util.infra;

import com.agentengine.util.common.beans.BaseEntity;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "_t")
public abstract class InfraConfig extends BaseEntity {
  private String type;
  private Integer customerId;
  private String serverId;

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public Integer getCustomerId() {
    return customerId;
  }

  public void setCustomerId(final Integer customerId) {
    this.customerId = customerId;
  }

  public String getServerId() {
    return serverId;
  }

  public void setServerId(final String serverId) {
    this.serverId = serverId;
  }
}
