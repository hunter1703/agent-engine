package com.agentengine.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlamaCppServerUtilsTest {

  @Test
  void resolveAddressReadsHostAndPort() {
    LlamaCppServerUtils.ServerAddress address = LlamaCppServerUtils.resolveAddress("http://127.0.0.1:17004/v1");

    assertThat(address.host()).isEqualTo("127.0.0.1");
    assertThat(address.port()).isEqualTo(17004);
  }

  @Test
  void resolveAddressDefaultsPortForHttp() {
    LlamaCppServerUtils.ServerAddress address = LlamaCppServerUtils.resolveAddress("http://localhost/v1");

    assertThat(address.port()).isEqualTo(80);
  }
}
