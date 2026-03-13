package com.agentengine.connectors.core.runtime;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.BaseEntity;
import java.util.Map;

public class Connection extends BaseEntity {
  private String appName;
  private Map<String, Object> inputs = Map.of();

  public Connection() {
  }

  public Connection(final String appName, final Map<String, Object> inputs) {
    this.appName = appName;
    this.inputs = CollectionUtils.nullSafeMutableMap(inputs);
  }

  public String appName() {
    return appName;
  }

  public Map<String, Object> inputs() {
    return inputs;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(final String appName) {
    this.appName = appName;
  }

  public Map<String, Object> getInputs() {
    return inputs;
  }

  public void setInputs(final Map<String, Object> inputs) {
    this.inputs = CollectionUtils.nullSafeMutableMap(inputs);
  }
}
