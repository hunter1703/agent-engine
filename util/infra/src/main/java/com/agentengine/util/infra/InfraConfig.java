package com.agentengine.util.infra;

import com.agentengine.util.common.beans.BaseEntity;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "_t")
public abstract class InfraConfig extends BaseEntity {
  private String category;
  private String type;

  public String getCategory() {
    return category;
  }

  public void setCategory(final String category) {
    this.category = category;
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }
}
