package com.agentengine.engine.api.beans.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.junit.jupiter.api.Test;

class OpenApiSchemaInheritanceTest {

  @Test
  void sessionServiceConfigDefinesDiscriminatorMappings() {
    final Schema schema = SessionServiceConfig.class.getAnnotation(Schema.class);

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
    final Schema schema = ContextManagerConfig.class.getAnnotation(Schema.class);

    assertThat(schema).isNotNull();
    assertThat(schema.discriminatorProperty()).isEqualTo("type");
    assertThat(schema.oneOf()).containsExactly(LastNContextManagerConfig.class);
    assertThat(schema.discriminatorMapping()).extracting(DiscriminatorMapping::value).containsExactly("last_n");
    assertThat(schema.discriminatorMapping()).extracting(DiscriminatorMapping::schema)
        .containsExactly(LastNContextManagerConfig.class);
  }
}
