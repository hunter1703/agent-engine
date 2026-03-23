package com.agentengine.util.pekko;

import org.apache.pekko.actor.typed.ActorSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActorSystemProviderTest {

  private ActorSystem<Void> system;

  @AfterEach
  void teardown() {
    if (system != null) {
      system.terminate();
    }
  }

  @Test
  void shouldCreateActorSystem() {
    final var config = new PekkoBaseConfig() {
      @Override
      public String hostname() {
        return "localhost";
      }
      @Override
      public int port() {
        return 0;
      }
      @Override
      public List<String> seedNodes() {
        return List.of();
      }
      @Override
      public String clusterName() {
        return "test-cluster";
      }
      @Override
      public String jdbcUrl() {
        return "";
      }
      @Override
      public String jdbcUser() {
        return "";
      }
      @Override
      public String jdbcPassword() {
        return "";
      }
    };
    system = new ActorSystemProvider(config).actorSystem();

    assertThat(system).isNotNull();
    assertThat(system.name()).isEqualTo("test-cluster");
  }
}
