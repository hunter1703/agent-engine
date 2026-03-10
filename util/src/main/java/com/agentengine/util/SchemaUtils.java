package com.agentengine.util;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import io.vertx.json.schema.common.dsl.ArraySchemaBuilder;
import io.vertx.json.schema.common.dsl.Keyword;
import io.vertx.json.schema.common.dsl.ObjectSchemaBuilder;
import io.vertx.json.schema.common.dsl.SchemaBuilder;
import io.vertx.json.schema.common.dsl.Schemas;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SchemaUtils {

  private SchemaUtils() {}

  public static String toJsonSchema(final Schema schema) {
    final SchemaBuilder<?, ?> vertxSchema = toVertxSchema(schema);
    final HashMap<String, Object> jsonSchemaMap = toMap(vertxSchema);
    return JsonUtils.toJson(jsonSchemaMap);
  }

  @SuppressWarnings("unchecked")
  public static HashMap<String, Object> toMap(final SchemaBuilder<?, ?> builder) {
    final HashMap<String, Object> jsonSchemaMap =
        new HashMap<>((Map<String, Object>) builder.toJson().mapTo(Map.class));
    JsonUtils.removeValue(jsonSchemaMap, "$..['$id']");
    return jsonSchemaMap;
  }

  public static SchemaBuilder<? extends SchemaBuilder<?, ?>, ? extends Keyword> toVertxSchema(
      final Schema schema) {
    if (schema == null) {
      return Schemas.schema();
    }
    final Optional<List<Schema>> anyOf = schema.anyOf();
    if (anyOf.isPresent() && !anyOf.get().isEmpty()) {
      final List<SchemaBuilder<?, ?>> anyOfSchemas = new ArrayList<>();
      for (final Schema entry : anyOf.get()) {
        anyOfSchemas.add(toVertxSchema(entry));
      }
      if (schema.nullable().orElse(false)) {
        anyOfSchemas.add(nullSchemaBuilder());
      }
      final SchemaBuilder<?, ?> anyOfBuilder =
          Schemas.anyOf(anyOfSchemas.toArray(new SchemaBuilder[0]));
      applyCommonKeywords(anyOfBuilder, schema);
      return anyOfBuilder;
    }
    final SchemaBuilder<?, ?> builder = applyNullable(buildSchemaBuilder(schema), schema);
    applyCommonKeywords(builder, schema);
    return builder;
  }

  private static SchemaBuilder<?, ?> buildSchemaBuilder(final Schema schema) {
    final Type.Known knownType =
        schema.type().map(Type::knownEnum).orElse(Type.Known.TYPE_UNSPECIFIED);
    return switch (knownType) {
      case STRING -> Schemas.stringSchema();
      case INTEGER -> Schemas.intSchema();
      case NUMBER -> Schemas.numberSchema();
      case BOOLEAN -> Schemas.booleanSchema();
      case ARRAY -> buildArraySchema(schema);
      case OBJECT -> buildObjectSchema(schema);
      case NULL -> nullSchemaBuilder();
      case TYPE_UNSPECIFIED -> buildFallbackSchema(schema);
    };
  }

  private static SchemaBuilder<?, ?> buildFallbackSchema(final Schema schema) {
    if (schema.properties().isPresent()) {
      return buildObjectSchema(schema);
    }
    if (schema.items().isPresent()) {
      return buildArraySchema(schema);
    }
    return Schemas.schema();
  }

  private static ArraySchemaBuilder buildArraySchema(final Schema schema) {
    final ArraySchemaBuilder builder = Schemas.arraySchema();
    schema.items().ifPresent(items -> builder.items(toVertxSchema(items)));
    return builder;
  }

  private static ObjectSchemaBuilder buildObjectSchema(final Schema schema) {
    final ObjectSchemaBuilder builder = Schemas.objectSchema();
    final Map<String, Schema> properties = schema.properties().orElse(Map.of());
    final List<String> required = schema.required().orElse(List.of());
    for (final Map.Entry<String, Schema> entry : properties.entrySet()) {
      final SchemaBuilder<?, ?> propertyBuilder = toVertxSchema(entry.getValue());
      if (required.contains(entry.getKey())) {
        builder.requiredProperty(entry.getKey(), propertyBuilder);
      } else {
        builder.optionalProperty(entry.getKey(), propertyBuilder);
      }
    }
    return builder;
  }

  private static void applyCommonKeywords(final SchemaBuilder<?, ?> builder, final Schema schema) {
    schema.description().ifPresent(value -> builder.withKeyword("description", value));
    schema.title().ifPresent(value -> builder.withKeyword("title", value));
    schema.format().ifPresent(value -> builder.withKeyword("format", value));
    schema.default_().ifPresent(value -> builder.defaultValue(value));
    schema.example().ifPresent(value -> builder.withKeyword("example", value));
    schema
        .enum_()
        .filter(values -> !values.isEmpty())
        .ifPresent(values -> builder.withKeyword("enum", values));
    schema.pattern().ifPresent(value -> builder.withKeyword("pattern", value));
    schema.minimum().ifPresent(value -> builder.withKeyword("minimum", value));
    schema.maximum().ifPresent(value -> builder.withKeyword("maximum", value));
    schema.minLength().ifPresent(value -> builder.withKeyword("minLength", value));
    schema.maxLength().ifPresent(value -> builder.withKeyword("maxLength", value));
    schema.minItems().ifPresent(value -> builder.withKeyword("minItems", value));
    schema.maxItems().ifPresent(value -> builder.withKeyword("maxItems", value));
    schema.minProperties().ifPresent(value -> builder.withKeyword("minProperties", value));
    schema.maxProperties().ifPresent(value -> builder.withKeyword("maxProperties", value));
    schema
        .propertyOrdering()
        .filter(values -> !values.isEmpty())
        .ifPresent(values -> builder.withKeyword("propertyOrdering", values));
  }

  private static SchemaBuilder<?, ?> applyNullable(
      final SchemaBuilder<?, ?> builder, final Schema schema) {
    if (!schema.nullable().orElse(false)) {
      return builder;
    }
    if (builder.getType() != null) {
      builder.nullable();
      return builder;
    }
    return Schemas.anyOf(builder, nullSchemaBuilder());
  }

  private static SchemaBuilder<?, ?> nullSchemaBuilder() {
    return Schemas.schema().withKeyword("type", "null");
  }
}
