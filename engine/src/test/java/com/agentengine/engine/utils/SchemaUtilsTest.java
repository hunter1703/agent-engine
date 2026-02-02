package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.common.dsl.SchemaBuilder;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaUtilsTest {

  @Test
  void convertsObjectSchemaWithProperties() {
    final Schema schema = Schema.builder().type(Type.Known.OBJECT).description("User payload")
        .properties(Map.of("name", Schema.builder().type(Type.Known.STRING).description("User name").build(), "age",
            Schema.builder().type(Type.Known.INTEGER).minimum(18D).build()))
        .required(List.of("name")).build();

    final SchemaBuilder<?, ?> builder = SchemaUtils.toVertxSchema(schema);
    final JsonObject json = builder.toJson();

    assertThat(json.getString("type")).isEqualTo("object");
    assertThat(json.getString("description")).isEqualTo("User payload");
    assertThat(json.getJsonArray("required")).contains("name");

    final JsonObject properties = json.getJsonObject("properties");
    assertThat(properties.getJsonObject("name").getString("type")).isEqualTo("string");
    assertThat(properties.getJsonObject("name").getString("description")).isEqualTo("User name");
    assertThat(properties.getJsonObject("age").getString("type")).isEqualTo("integer");
    assertThat(properties.getJsonObject("age").getDouble("minimum")).isEqualTo(18D);
  }

  @Test
  void addsNullableToAnyOfSchemas() {
    final Schema schema = Schema.builder()
        .anyOf(Schema.builder().type(Type.Known.STRING), Schema.builder().type(Type.Known.INTEGER)).nullable(true)
        .build();

    final JsonObject json = SchemaUtils.toVertxSchema(schema).toJson();
    final JsonArray anyOf = json.getJsonArray("anyOf");

    assertThat(anyOf).hasSize(3);
    assertThat(anyOf.stream().map(entry -> ((JsonObject) entry).getString("type")).toList()).contains("string",
        "integer", "null");
  }
}
