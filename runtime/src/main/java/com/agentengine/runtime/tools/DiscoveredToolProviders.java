package com.agentengine.runtime.tools;

import com.agentengine.runtime.annotations.ToolConstructor;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.utils.SchemaUtils;
import com.agentengine.runtime.utils.ToolUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.common.StringUtils;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.Schema;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InstanceHandle;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public final class DiscoveredToolProviders {
    private final LazyLoader<List<ToolProvider>> providers;

    @Inject
    public DiscoveredToolProviders(final @Any Instance<Tool> tools) {
        this.providers = new LazyLoader<>(() -> {
            final Map<String, ToolProvider> resolvedProviders = new LinkedHashMap<>();
            for (final Tool tool : tools) {
                try {
                    final ToolDefinition definition = buildDefinition(tool.getClass());
                    resolvedProviders.putIfAbsent(
                            definition.descriptor().name(),
                            new DiscoveredToolProvider(definition));
                } finally {
                    tools.destroy(tool);
                }
            }
            return List.copyOf(resolvedProviders.values());
        });
    }

    private static ToolDefinition buildDefinition(final Class<? extends Tool> toolClass) {
        final Constructor<? extends Tool> constructor = resolveConstructor(toolClass);
        final List<ConstructorParam> params = resolveParameters(constructor);
        final ToolDescriptor descriptor = getDescriptor(toolClass, params);
        return new ToolDefinition(descriptor, constructor, params);
    }

    private static Constructor<? extends Tool> resolveConstructor(final Class<? extends Tool> toolClass) {
        final List<Constructor<? extends Tool>> constructors = new ArrayList<>();
        final List<Constructor<? extends Tool>> annotated = new ArrayList<>();
        for (final Constructor<?> constructor : toolClass.getDeclaredConstructors()) {
            @SuppressWarnings("unchecked")
            final Constructor<? extends Tool> typedConstructor = (Constructor<? extends Tool>) constructor;
            constructors.add(typedConstructor);
            if (constructor.isAnnotationPresent(ToolConstructor.class)) {
                annotated.add(typedConstructor);
            }
        }
        if (annotated.size() > 1) {
            throw new IllegalStateException("Multiple @ToolConstructor annotations found for " + toolClass.getName());
        }
        final Constructor<? extends Tool> resolved;
        if (annotated.size() == 1) {
            resolved = CollectionUtils.getFirst(annotated);
        } else if (constructors.size() == 1) {
            resolved = CollectionUtils.getFirst(constructors);
        } else {
            throw new IllegalStateException(
                    "Multiple constructors found for " + toolClass.getName() + "; annotate one with @ToolConstructor");
        }
        Objects.requireNonNull(resolved).setAccessible(true);
        return resolved;
    }

    private static List<ConstructorParam> resolveParameters(final Constructor<? extends Tool> constructor) {
        final Parameter[] parameters = constructor.getParameters();
        final List<ConstructorParam> params = new ArrayList<>(parameters.length);
        for (final Parameter parameter : parameters) {
            final boolean injected = isCdiBean(parameter.getType());
            final String key = injected ? parameter.getType().getSimpleName() : resolveKey(parameter);
            final ToolSchema schema = parameter.getAnnotation(ToolSchema.class);
            final String description = schema == null ? null : schema.description();
            final boolean optional = schema != null && schema.optional();
            final List<String> enumValues =
                    schema == null ? List.of() : Arrays.stream(schema.enums()).toList();
            params.add(new ConstructorParam(
                    key,
                    parameter.getParameterizedType(),
                    parameter.getType(),
                    description,
                    optional,
                    enumValues,
                    injected));
        }
        return params;
    }

    private static String resolveKey(final Parameter parameter) {
        final ToolSchema annotation = parameter.getAnnotation(ToolSchema.class);
        final String key = annotation == null ? "" : annotation.name();
        if (StringUtils.isNotBlank(key)) {
            return key;
        }
        final String name = parameter.getName();
        if (StringUtils.isBlank(name)) {
            throw new IllegalStateException("Tool parameter name is required for auto discovery");
        }
        return name;
    }

    private static boolean isCdiBean(final Class<?> type) {
        final ArcContainer container = Arc.container();
        if (container == null) {
            return false;
        }
        try (final InstanceHandle<?> instance = container.instance(type)) {
            return instance.isAvailable();
        } catch (Exception ignored) {
            return false;
        }
    }

    public List<ToolProvider> providers() {
        return providers.get();
    }

    private static ToolDescriptor getDescriptor(
            final Class<? extends Tool> toolClass, final List<ConstructorParam> params) {
        final ToolDescriptor descriptor = resolveBaseDescriptor(toolClass);
        final Map<String, Object> configsSchema = buildConfigsSchema(params, descriptor.configsSchema());
        return new ToolDescriptor(descriptor.name(), descriptor.description(), configsSchema, descriptor.riskLevel());
    }

    private static ToolDescriptor resolveBaseDescriptor(final Class<? extends Tool> toolClass) {
        final ToolDescriptor staticDescriptor = resolveStaticDescriptor(toolClass);
        if (staticDescriptor != null) {
            return staticDescriptor;
        }
        try {
            final Constructor<? extends Tool> constructor = toolClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            final Tool tool = constructor.newInstance();
            return tool.descriptor();
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(
                    "No-arg constructor required for descriptor resolution in " + toolClass.getName(), exception);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException(
                    "Failed to instantiate " + toolClass.getName() + " for descriptor resolution", exception);
        }
    }

    private static Map<String, Object> buildConfigsSchema(
            final List<ConstructorParam> params, final Map<String, Object> defaultSchema) {
        if (CollectionUtils.isEmpty(params)) {
            return defaultSchema;
        }
        final List<String> required = new ArrayList<>();
        final Map<String, Schema> properties = new LinkedHashMap<>();
        for (final ConstructorParam param : params) {
            if (param.injected()) {
                continue;
            }
            if (!param.optional()) {
                required.add(param.key());
            }
            Schema propertySchema = ToolUtils.buildSchemaFromType(param.type());
            if (!param.enumValues().isEmpty()) {
                propertySchema =
                        propertySchema.toBuilder().enum_(param.enumValues()).build();
            }
            propertySchema = ToolUtils.applySchemaMetadata(
                    propertySchema, param.description(), !param.optional(), param.enumValues());
            properties.put(param.key(), propertySchema);
        }
        if (properties.isEmpty()) {
            return defaultSchema;
        }
        final Schema.Builder builder = Schema.builder().type("OBJECT").properties(properties);
        if (!required.isEmpty()) {
            builder.required(required);
        }
        return SchemaUtils.toMap(builder.build());
    }

    private static ToolDescriptor resolveStaticDescriptor(final Class<? extends Tool> toolClass) {
        try {
            final Field descriptorField = toolClass.getDeclaredField("DESCRIPTOR");
            if (!Modifier.isStatic(descriptorField.getModifiers())
                    || !ToolDescriptor.class.isAssignableFrom(descriptorField.getType())) {
                return null;
            }
            descriptorField.setAccessible(true);
            return (ToolDescriptor) descriptorField.get(null);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            return null;
        }
    }

    private record ToolDefinition(
            ToolDescriptor descriptor, Constructor<? extends Tool> constructor, List<ConstructorParam> params) {}

    private record ConstructorParam(
            String key,
            Type type,
            Class<?> rawType,
            String description,
            boolean optional,
            List<String> enumValues,
            boolean injected) {}

    private record DiscoveredToolProvider(ToolDefinition definition) implements ToolProvider {
        @Override
        public ToolDescriptor descriptor() {
            return definition.descriptor();
        }

        @Override
        public BaseTool create(final Map<String, Object> toolConfig) {
            final Object[] args = resolveArguments(toolConfig);
            try {
                return definition.constructor().newInstance(args);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
                final String toolName = definition.descriptor().name();
                throw new IllegalStateException("Failed to create tool " + toolName, exception);
            }
        }

        private Object[] resolveArguments(final Map<String, Object> toolConfig) {
            final List<ConstructorParam> params = definition.params();
            if (CollectionUtils.isEmpty(params)) {
                return new Object[0];
            }
            final Object[] args = new Object[params.size()];
            for (int index = 0; index < params.size(); index++) {
                final ConstructorParam param = params.get(index);
                if (param.injected()) {
                    try (final InstanceHandle<?> instance = Arc.container().instance(param.rawType())) {
                        args[index] = instance.get();
                        continue;
                    }
                }
                final Object rawValue = CollectionUtils.getValueFromMap(toolConfig, param.key());
                args[index] = convertValue(rawValue, param.type(), param.rawType());
            }
            return args;
        }

        @SuppressWarnings("unchecked")
        private static Object convertValue(final Object value, final Type type, final Class<?> rawType) {
            if (value == null) {
                return getDefaultValue(rawType);
            }
            if (rawType.isInstance(value)) {
                return value;
            }
            if (rawType.isEnum() && value instanceof String text) {
                @SuppressWarnings("rawtypes")
                final Class<? extends Enum> enumType = (Class<? extends Enum>) rawType;
                return Enum.valueOf(enumType, text.trim().toUpperCase());
            }
            return JsonUtils.fromJson(JsonUtils.toJson(value), type);
        }

        private static Object getDefaultValue(final Class<?> rawType) {
            if (!rawType.isPrimitive()) {
                return null;
            }
            if (rawType == boolean.class) {
                return false;
            }
            if (rawType == char.class) {
                return '\0';
            }
            if (rawType == byte.class) {
                return (byte) 0;
            }
            if (rawType == short.class) {
                return (short) 0;
            }
            if (rawType == int.class) {
                return 0;
            }
            if (rawType == long.class) {
                return 0L;
            }
            if (rawType == float.class) {
                return 0F;
            }
            if (rawType == double.class) {
                return 0D;
            }
            return null;
        }
    }
}
