package com.agentengine.util.mongodb.infra;

public interface InfraConfigService {

    <T extends InfraConfig> T findById(String configCategory, String configType, String configId);
}
