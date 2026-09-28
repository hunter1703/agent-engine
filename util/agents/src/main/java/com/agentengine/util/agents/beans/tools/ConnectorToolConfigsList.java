package com.agentengine.util.agents.beans.tools;

import com.agentengine.util.agents.builder.annotations.UiField;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;

public final class ConnectorToolConfigsList {

  @UiField(label = "Connectors", order = 10)
  @Valid
  private List<ConnectorToolConfig> connectors = new ArrayList<>();

  public List<ConnectorToolConfig> getConnectors() {
    return connectors;
  }

  public void setConnectors(final List<ConnectorToolConfig> connectors) {
    this.connectors = connectors == null ? new ArrayList<>() : new ArrayList<>(connectors);
  }
}
