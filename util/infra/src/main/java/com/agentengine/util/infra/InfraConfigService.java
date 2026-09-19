package com.agentengine.util.infra;

import java.util.List;

public interface InfraConfigService {

  <T extends InfraConfig> T get(String id);

  default <S extends InfraConfig> S getServer(final String serverType, final InfraConfig client) {
    return get(serverType + ":" + client.getServerId());
  }

  List<InfraConfig> saveAll(List<InfraConfig> configs);
}
