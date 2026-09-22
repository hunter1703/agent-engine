package com.agentengine.util.infra;

public interface InfraConfigService {

  <T extends InfraConfig> T get(String id);

  default <S extends InfraConfig> S getServer(
      final ServerType serverType, final InfraConfig client) {
    return get(serverType + ":" + client.getServerId());
  }

  <T extends InfraConfig> T save(T config);

  void insert(InfraConfig config);
}
