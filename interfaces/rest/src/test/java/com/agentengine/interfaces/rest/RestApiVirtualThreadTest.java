package com.agentengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.interfaces.rest.catalog.ResourceCatalogAPI;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.junit.jupiter.api.Test;

class RestApiVirtualThreadTest {

  @Test
  void restApisRunOnVirtualThread() {
    assertThat(AgentRestAPI.class.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
    assertThat(ModelRestAPI.class.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
    assertThat(SchemaRestAPI.class.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
    assertThat(ResourceCatalogAPI.class.isAnnotationPresent(RunOnVirtualThread.class)).isTrue();
  }
}
