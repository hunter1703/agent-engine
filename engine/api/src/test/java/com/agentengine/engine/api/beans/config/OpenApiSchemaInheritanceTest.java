package com.agentengine.engine.api.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.junit.jupiter.api.Test;

class OpenApiSchemaInheritanceTest {

  @Test
  void sessionServiceConfigDefinesDiscriminatorMappings() {
    final org.eclipse.microprofile.openapi.annotations.media.Schema schema = SessionServiceConfig.class
        .getAnnotation(org.eclipse.microprofile.openapi.annotations.media.Schema.class);

    assertThat(schema).isNotNull();
    assertThat(schema.discriminatorProperty()).isEqualTo("type");
    assertThat(schema.oneOf()).contains(InMemorySessionServiceConfig.class, MongoSessionServiceConfig.class);
    assertThat(schema.discriminatorMapping()).extracting(DiscriminatorMapping::value)
        .containsExactlyInAnyOrder("memory", "mongodb");
    assertThat(schema.discriminatorMapping()).extracting(DiscriminatorMapping::schema)
        .containsExactlyInAnyOrder(InMemorySessionServiceConfig.class, MongoSessionServiceConfig.class);
  }

  @Test
  void contextManagerConfigDefinesDiscriminatorMappings() {
    final org.eclipse.microprofile.openapi.annotations.media.Schema schema = ContextManagerConfig.class
        .getAnnotation(org.eclipse.microprofile.openapi.annotations.media.Schema.class);

    assertThat(schema).isNotNull();
    assertThat(schema.discriminatorProperty()).isEqualTo("type");
    assertThat(schema.oneOf()).containsExactly(LastNContextManagerConfig.class);
    assertThat(schema.discriminatorMapping()).extracting(DiscriminatorMapping::value).containsExactly("last_n");
    assertThat(schema.discriminatorMapping()).extracting(DiscriminatorMapping::schema)
        .containsExactly(LastNContextManagerConfig.class);
  }
}
