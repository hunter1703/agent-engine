package com.agentengine.util.infra;

public interface InfraSetup<C extends InfraConfig> {

  Class<C> configType();

  void setup(C config);

  default void setupIfMatches(final InfraConfig config) {
    if (configType().isInstance(config)) {
      setup(configType().cast(config));
    }
  }
}
