package com.agentengine.util.infra.provisioning;

import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.infra.ServerType;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;

public class ProvisioningRequest {
  private Map<String, Server> typeVsServer = new HashMap<>();

  // What a downstream ProvisioningService needs beyond typeVsServer, keyed by whatever name it
  // chooses — captured here instead of a named field so this class never has to know what any
  // of its own implementations need.
  private final Map<String, Object> additional = new HashMap<>();

  public Map<String, Server> getTypeVsServer() {
    return typeVsServer;
  }

  public void setTypeVsServer(Map<String, Server> typeVsServer) {
    this.typeVsServer = typeVsServer;
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditional() {
    return additional;
  }

  @JsonAnySetter
  public void putAdditional(final String key, final Object value) {
    additional.put(key, value);
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
