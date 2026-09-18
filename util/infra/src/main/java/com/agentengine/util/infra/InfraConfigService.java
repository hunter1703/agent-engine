package com.agentengine.util.infra;

import java.util.List;

public interface InfraConfigService {

  <T extends InfraConfig> T findById(String configCategory, String configType, String configId);

  List<InfraConfig> saveAll(List<InfraConfig> configs);
}
