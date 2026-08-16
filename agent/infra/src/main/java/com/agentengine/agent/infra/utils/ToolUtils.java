package com.agentengine.agent.infra.utils;

import static com.agentengine.util.common.Utils.*;

import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Utils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.*;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ToolUtils {
  private static final Logger LOG = LoggerFactory.getLogger(ToolUtils.class);

  private ToolUtils() {}

  public static String resolveParameterName(final Parameter parameter) {
    if (parameter.isAnnotationPresent(ToolSchema.class)) {
      String name = parameter.getAnnotation(ToolSchema.class).name();
      if (StringUtils.isNotBlank(name)) {
        return name;
      }
    }
    return parameter.getName();
  }

  public static FunctionDeclaration buildFunctionDeclaration(
      final Method method, final ToolDescriptor toolDescriptor) {
    final FunctionDeclaration.Builder builder =
        FunctionDeclaration.builder()
            .name(toolDescriptor.name())
            .description(toolDescriptor.description());

    builder.parameters(buildParameterObjectSchema(method.getParameters(), false));
    builder.response(buildResponseSchema(method));
    return builder.build();
  }

  public static Schema buildParameterObjectSchema(
      final Parameter[] parameters, final boolean includeUnannotated) {
    final List<String> required = new ArrayList<>();
    final Map<String, Schema> properties = new LinkedHashMap<>();
    for (final Parameter param : parameters) {
      final String paramName = resolveParameterName(param);
      if (shouldIgnoreParameter(paramName)) {
        continue;
      }
      final ToolSchema schema = param.getAnnotation(ToolSchema.class);
      if (schema == null && !includeUnannotated) {
        continue;
      }
      final boolean isRequired = schema == null || !schema.optional();
      if (isRequired) {
        required.add(paramName);
      }
      Schema paramSchema = buildSchemaFromType(param.getParameterizedType());
      final List<String> enumValues =
          getEnumValues(schema == null ? null : schema.enums(), paramSchema);
      paramSchema = addEnumsToSchema(enumValues, paramSchema);
      paramSchema = applyFieldMetadata(paramSchema, schema, isRequired, enumValues);
      properties.put(paramName, paramSchema);
    }

    final Schema.Builder builder = Schema.builder().properties(properties).type("OBJECT");
    if (!required.isEmpty()) {
      builder.required(required);
    }
    return builder.build();
  }

  private static boolean shouldIgnoreParameter(final String paramName) {
    return "toolContext".equals(paramName) || "inputStream".equals(paramName);
  }

  private static List<String> getEnumValues(String[] enumValuesArr, final Schema paramSchema) {
    List<String> enumValues =
        enumValuesArr == null ? null : Arrays.stream(enumValuesArr).collect(Collectors.toList());
    if (CollectionUtils.isEmpty(enumValues) && paramSchema.enum_().isPresent()) {
      return paramSchema.enum_().orElse(List.of());
    }
    return CollectionUtils.nullSafeList(enumValues);
  }

  private static Schema addEnumsToSchema(final List<String> enumValues, Schema paramSchema) {
    if (!enumValues.isEmpty()) {
      paramSchema = paramSchema.toBuilder().enum_(enumValues).build();
    }
    return paramSchema;
  }

  public static Schema applySchemaMetadata(
      final Schema schema,
      final String description,
      final boolean required,
      final List<String> enumValues) {
    return applyFieldMetadata(schema, description, required, enumValues);
  }

  private static Schema buildResponseSchema(final Method method) {
    Type returnType = method.getGenericReturnType();
    if (returnType == Void.TYPE || returnType == Void.class) {
      return Schema.builder().type("NULL").build();
    }
    Type actualReturnType = returnType;
    if (returnType instanceof ParameterizedType parameterizedReturnType) {
      String rawTypeName = ((Class<?>) parameterizedReturnType.getRawType()).getName();
      if (rawTypeName.equals("io.reactivex.rxjava3.core.Maybe")
          || rawTypeName.equals("io.reactivex.rxjava3.core.Single")
          || rawTypeName.equals("io.reactivex.rxjava3.core.Flowable")) {
        actualReturnType = parameterizedReturnType.getActualTypeArguments()[0];
      }
    }
    return buildSchemaFromType(actualReturnType);
  }

  public static Schema buildSchemaFromType(final Type type) {
    return buildSchemaRecursive(Utils.constructType(type), new SchemaContext());
  }

  private static Schema buildSchemaRecursive(final JavaType javaType, final SchemaContext context) {
    if (context.isProcessing(javaType)) {
      return Schema.builder()
          .type("OBJECT")
          .description("Recursive reference to " + javaType.toCanonical() + " omitted.")
          .build();
    }
    final Optional<Schema> cachedSchema = context.getDefinition(javaType);
    if (cachedSchema.isPresent()) {
      return cachedSchema.get();
    }

    context.startProcessing(javaType);
    try {
      Schema resultSchema;
      Schema.Builder builder = Schema.builder();
      Class<?> rawClass = javaType.getRawClass();

      if (javaType.isCollectionLikeType() && List.class.isAssignableFrom(rawClass)) {
        builder.type("ARRAY").items(buildSchemaRecursive(javaType.getContentType(), context));
      } else if (javaType.isMapLikeType()) {
        builder.type("OBJECT");
      } else if (String.class.equals(rawClass)) {
        builder.type("STRING");
      } else if (Boolean.class.equals(rawClass) || boolean.class.equals(rawClass)) {
        builder.type("BOOLEAN");
      } else if (Integer.class.equals(rawClass)
          || int.class.equals(rawClass)
          || Long.class.equals(rawClass)
          || long.class.equals(rawClass)) {
        builder.type("INTEGER");
      } else if (Double.class.equals(rawClass)
          || double.class.equals(rawClass)
          || Float.class.equals(rawClass)
          || float.class.equals(rawClass)) {
        builder.type("NUMBER");
      } else if (rawClass.isEnum()) {
        List<String> enumValues = new ArrayList<>();
        for (Object enumConstant : rawClass.getEnumConstants()) {
          enumValues.add(enumConstant.toString());
        }
        builder.enum_(enumValues).type("STRING").format("enum");
      } else {
        populateObjectSchema(builder, javaType, context);
      }

      resultSchema = builder.build();
      context.addDefinition(javaType, resultSchema);
      return resultSchema;
    } finally {
      context.finishProcessing(javaType);
    }
  }

  private static void populateObjectSchema(
      final Schema.Builder builder, final JavaType javaType, final SchemaContext context) {
    BeanDescription beanDescription = Utils.getBeanDescription(javaType);
    Map<String, Schema> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();
    for (BeanPropertyDefinition property : beanDescription.findProperties()) {
      AnnotatedMember member = resolveMember(property);
      if (member == null) {
        continue;
      }
      ToolSchema toolField = findAnnotation(property, ToolSchema.class);
      String propertyName = property.getName();
      if (toolField != null && StringUtils.isNotBlank(toolField.name())) {
        propertyName = toolField.name();
      }
      Schema propertySchema = buildSchemaRecursive(member.getType(), context);
      final List<String> enumValues =
          getEnumValues(toolField == null ? null : toolField.enums(), propertySchema);
      propertySchema = addEnumsToSchema(enumValues, propertySchema);
      boolean requiredProperty = isRequired(property, toolField);
      propertySchema = applyFieldMetadata(propertySchema, toolField, requiredProperty, enumValues);
      properties.put(propertyName, propertySchema);
      if (requiredProperty) {
        required.add(propertyName);
      }
    }
    builder.type("OBJECT").properties(properties);
    if (!required.isEmpty()) {
      builder.required(required);
    }
  }

  private static boolean isRequired(
      final BeanPropertyDefinition property, final ToolSchema toolField) {
    if (toolField != null) {
      return !toolField.optional();
    }
    return property.isRequired();
  }

  private static Schema applyFieldMetadata(
      final Schema schema,
      final ToolSchema toolSchema,
      final boolean required,
      final List<String> enumValues) {
    String baseDescription = toolSchema == null ? null : toolSchema.description();
    return applyFieldMetadata(schema, baseDescription, required, enumValues);
  }

  private static Schema applyFieldMetadata(
      final Schema schema,
      String baseDescription,
      final boolean required,
      final List<String> enumValues) {
    if (StringUtils.isBlank(baseDescription) && schema.description().isPresent()) {
      baseDescription = schema.description().orElse("");
    }
    List<String> parts = new ArrayList<>();
    if (StringUtils.isNotBlank(baseDescription)) {
      parts.add(baseDescription.trim());
    }
    parts.add(required ? "Required." : "Optional.");
    if (enumValues != null && !enumValues.isEmpty()) {
      parts.add("Possible values: " + String.join(", ", enumValues) + ".");
    }
    String description = String.join(" ", parts).trim();
    if (StringUtils.isBlank(description)) {
      return schema;
    }
    return schema.toBuilder().description(description).build();
  }

  public static List<FunctionCall> extractToolCalls(final LlmResponse response) {
    return response.content().flatMap(Content::parts).stream()
        .flatMap(List::stream)
        .map(Part::functionCall)
        .flatMap(Optional::stream)
        .toList();
  }

  public static String agentId(final ToolContext toolContext) {
    if (toolContext == null
        || toolContext.invocationContext() == null
        || toolContext.invocationContext().agent() == null
        || toolContext.invocationContext().agent().name() == null) {
      throw new IllegalStateException(
          "Agent tools require an active invocation context with an agent id.");
    }
    return toolContext.invocationContext().agent().name();
  }

  public static String sessionId(final ToolContext toolContext) {
    if (toolContext == null
        || toolContext.invocationContext() == null
        || toolContext.invocationContext().session() == null
        || toolContext.invocationContext().session().id() == null) {
      throw new IllegalStateException(
          "Agent tools require an active invocation context with a sessionState id.");
    }
    return toolContext.invocationContext().session().id();
  }

  private static final class SchemaContext {
    private final Map<JavaType, Schema> definitions = new HashMap<>();
    private final Set<JavaType> processingStack = new HashSet<>();

    private boolean isProcessing(JavaType type) {
      return processingStack.contains(type);
    }

    private void startProcessing(JavaType type) {
      processingStack.add(type);
    }

    private void finishProcessing(JavaType type) {
      processingStack.remove(type);
    }

    private Optional<Schema> getDefinition(JavaType type) {
      return Optional.ofNullable(definitions.get(type));
    }

    private void addDefinition(JavaType type, Schema schema) {
      definitions.put(type, schema);
    }
  }
}
