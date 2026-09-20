package com.agentengine.tenancy;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProvisioningRequest {
  private Map<String, Server> typeVsServer = new HashMap<>();

  public Map<String, Server> getTypeVsServer() {
    return typeVsServer;
  }

  public void setTypeVsServer(Map<String, Server> typeVsServer) {
    this.typeVsServer = typeVsServer;
  }

  @JsonIgnore
  public String getServer(final String serverType, final String clientType) {
    final Server server = CollectionUtils.getValueFromMap(typeVsServer, serverType);
    final String clientServerId = CollectionUtils.getStringValueFromMap(Objects.requireNonNull(server).getClientTypeVsServerId(), clientType);
    return StringUtils.isEmpty(clientServerId) ? server.getDefaultServerId() : clientServerId;
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
