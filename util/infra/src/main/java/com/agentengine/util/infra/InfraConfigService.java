package com.agentengine.util.infra;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import java.util.List;

public interface InfraConfigService {

  <T extends InfraConfig> T get(String id);

  default <S extends InfraConfig> S getServer(
      final ServerType serverType, final InfraConfig client) {
    return get(serverType + ID_SEPARATOR + client.getServerId());
  }

  /** Every stored server config of {@code serverType}. */
  <T extends InfraConfig> List<T> findServers(ServerType serverType);

  <T extends InfraConfig> T save(T config);

  void insert(InfraConfig config);
}
