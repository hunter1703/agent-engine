package com.agentengine.tenancy;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.infra.ServerType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;

public class ProvisioningRequest {
  private Map<String, Server> typeVsServer = new HashMap<>();

  public Map<String, Server> getTypeVsServer() {
    return typeVsServer;
  }

  public void setTypeVsServer(Map<String, Server> typeVsServer) {
    this.typeVsServer = typeVsServer;
  }

  @JsonIgnore
  public String getServer(final ServerType serverType, final String clientType) {
    final Server server = CollectionUtils.getValueFromMap(typeVsServer, serverType.name());
    final String clientServerId =
        server == null
            ? null
            : CollectionUtils.getStringValueFromMap(server.getClientTypeVsServerId(), clientType);
    return StringUtils.isEmpty(clientServerId) ? getDefaultServerId(serverType) : clientServerId;
  }

  @JsonIgnore
  public String getDefaultServerId(final ServerType serverType) {
    final Server server = CollectionUtils.getValueFromMap(typeVsServer, serverType.name());
    return server == null ? null : server.getDefaultServerId();
  }

  public static class Server {
    private String defaultServerId;
    private Map<String, String> clientTypeVsServerId;

    public Map<String, String> getClientTypeVsServerId() {
      return clientTypeVsServerId;
    }

    public void setClientTypeVsServerId(Map<String, String> clientTypeVsServerId) {
      this.clientTypeVsServerId = clientTypeVsServerId;
    }

    public String getDefaultServerId() {
      return defaultServerId;
    }

    public void setDefaultServerId(String defaultServerId) {
      this.defaultServerId = defaultServerId;
    }
  }
}
