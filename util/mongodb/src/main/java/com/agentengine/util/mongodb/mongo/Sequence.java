package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.beans.BaseEntity;

public class Sequence extends BaseEntity {

  public static final String FIELD_VALUE = "value";

  private long value;

  public long getValue() {
    return value;
  }

  public void setValue(final long value) {
    this.value = value;
  }
}
