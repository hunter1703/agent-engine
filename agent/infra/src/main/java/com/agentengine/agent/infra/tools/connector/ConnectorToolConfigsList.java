package com.agentengine.agent.infra.tools.connector;

import com.agentengine.util.agents.builder.annotations.UiField;
import java.util.ArrayList;
import java.util.List;

public final class ConnectorToolConfigsList {

  @UiField(label = "Connectors", order = 10)
  private List<ConnectorToolConfig> connectors = new ArrayList<>();

  public List<ConnectorToolConfig> getConnectors() {
    return connectors;
  }

  public void setConnectors(final List<ConnectorToolConfig> connectors) {
    this.connectors = connectors == null ? new ArrayList<>() : new ArrayList<>(connectors);
  }
}
