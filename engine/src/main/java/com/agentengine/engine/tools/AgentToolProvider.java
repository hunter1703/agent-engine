package com.agentengine.engine.tools;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.annotations.AgentTool;
import com.agentengine.engine.api.tools.annotations.ToolConstructor;
import com.agentengine.engine.api.tools.annotations.ToolParam;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.JsonUtils;
import com.agentengine.engine.api.utils.StringUtils;
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
import java.util.*;

@Singleton
public final class AgentToolProvider implements ToolProvider {
    private final Map<String, ToolDefinition> definitions;
    private final List<ToolDescriptor> descriptors;

    @Inject
    public AgentToolProvider(final @Any Instance<Tool> tools) {
        final Map<String, ToolDefinition> resolvedDefinitions = new HashMap<>();
        final List<ToolDescriptor> resolvedDescriptors = new ArrayList<>();
        for (final Tool tool : tools) {
            try {
                if (!tool.getClass().isAnnotationPresent(AgentTool.class)) {
                    continue;
                }
                final ToolDefinition definition = buildDefinition(tool.getClass());
                if (resolvedDefinitions.putIfAbsent(definition.descriptor().name(), definition) == null) {
                    resolvedDescriptors.add(definition.descriptor());
                }
            } finally {
                tools.destroy(tool);
            }
        }
        this.definitions = Map.copyOf(resolvedDefinitions);
        this.descriptors = List.copyOf(resolvedDescriptors);
    }

    @Override
    public List<ToolDescriptor> tools() {
        return descriptors;
    }

    @Override
    public Tool create(
            final AgentContext agentContext,
            final String toolName,
            final Map<String, Object> toolConfig) {
        if (StringUtils.isBlank(toolName)) {
            return null;
        }
        final ToolDefinition definition = definitions.get(toolName);
        if (definition == null) {
            return null;
        }
        return instantiate(definition, agentContext, toolConfig);
    }

    private static Tool instantiate(
            final ToolDefinition definition,
            final AgentContext agentContext,
            final Map<String, Object> toolConfig) {
        final Object[] args = resolveArguments(definition, agentContext, toolConfig);
        try {
            return definition.constructor().newInstance(args);
        } catch (InstantiationException
                 | IllegalAccessException
                 | InvocationTargetException exception) {
            final String toolName =
                    definition.descriptor() == null
                            ? definition.constructor().getDeclaringClass().getName()
                            : definition.descriptor().name();
            throw new IllegalStateException(
                    "Failed to create tool " + toolName, exception);
        }
    }

    private static Object[] resolveArguments(
            final ToolDefinition definition,
            final AgentContext agentContext,
            final Map<String, Object> toolConfig) {
        final List<ConstructorParam> params = definition.params();
        if (CollectionUtils.isEmpty(params)) {
            return new Object[0];
        }
        final Object[] args = new Object[params.size()];
        for (int index = 0; index < params.size(); index++) {
            final ConstructorParam param = params.get(index);
            if (param.injectContext()) {
                args[index] = agentContext;
                continue;
            }
            final Object rawValue = CollectionUtils.getValueFromMap(toolConfig, param.key());
            args[index] = convertValue(rawValue, param.type(), param.rawType());
        }
        return args;
    }

    @SuppressWarnings("unchecked")
    private static Object convertValue(
            final Object value, final Type type, final Class<?> rawType) {
        if (value == null) {
            return getDefaultValue(rawType);
        }
        if (rawType.isInstance(value)) {
            return value;
        }
        if (rawType.isEnum() && value instanceof String text) {
            @SuppressWarnings("rawtypes") final Class<? extends Enum> enumType = (Class<? extends Enum>) rawType;
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

    private static ToolDefinition buildDefinition(final Class<? extends Tool> toolClass) {
        final Constructor<? extends Tool> constructor = resolveConstructor(toolClass);
        final List<ConstructorParam> params = resolveParameters(constructor);
        final ToolDescriptor descriptor = resolveDescriptor(toolClass);
        return new ToolDefinition(descriptor, constructor, params);
    }

    private static Constructor<? extends Tool> resolveConstructor(final Class<? extends Tool> toolClass) {
        final List<Constructor<? extends Tool>> constructors = new ArrayList<>();
        final List<Constructor<? extends Tool>> annotated = new ArrayList<>();
        for (final Constructor<?> constructor : toolClass.getDeclaredConstructors()) {
            @SuppressWarnings("unchecked") final Constructor<? extends Tool> typedConstructor = (Constructor<? extends Tool>) constructor;
            constructors.add(typedConstructor);
            if (constructor.isAnnotationPresent(ToolConstructor.class)) {
                annotated.add(typedConstructor);
            }
        }
        if (annotated.size() > 1) {
            throw new IllegalStateException(
                    STR."Multiple @ToolConstructor annotations found for \{toolClass.getName()}");
        }
        final Constructor<? extends Tool> resolved;
        if (annotated.size() == 1) {
            resolved = CollectionUtils.getFirst(annotated);
        } else if (constructors.size() == 1) {
            resolved = CollectionUtils.getFirst(constructors);
        } else {
            throw new IllegalStateException(
                    STR."Multiple constructors found for \{toolClass.getName()}; annotate one with @ToolConstructor");
        }
        Objects.requireNonNull(resolved).setAccessible(true);
        return resolved;
    }

    private static List<ConstructorParam> resolveParameters(final Constructor<? extends Tool> constructor) {
        final Parameter[] parameters = constructor.getParameters();
        final List<ConstructorParam> params = new ArrayList<>(parameters.length);
        for (final Parameter parameter : parameters) {
            final ToolParam annotation = parameter.getAnnotation(ToolParam.class);
            final String key = resolveKey(parameter, annotation);
            final boolean injectContext =
                    AgentContext.class.isAssignableFrom(parameter.getType())
                            || ToolParam.AGENT_CONTEXT.equals(key);
            if (injectContext && !AgentContext.class.isAssignableFrom(parameter.getType())) {
                throw new IllegalStateException(
                        STR."AgentContext injection requires AgentContext type for \{parameter.getName()}");
            }
            params.add(new ConstructorParam(key, parameter.getParameterizedType(), parameter.getType(), injectContext));
        }
        return params;
    }

    private static String resolveKey(final Parameter parameter, final ToolParam annotation) {
        final String key = annotation == null ? "" : annotation.value();
        if (StringUtils.isNotBlank(key)) {
            return key;
        }
        final String name = parameter.getName();
        if (StringUtils.isBlank(name)) {
            throw new IllegalStateException("Tool parameter name is required for auto discovery");
        }
        return name;
    }

    private static ToolDescriptor resolveDescriptor(final Class<? extends Tool> toolClass) {
        ToolDescriptor descriptor = resolveDescriptorField(toolClass);
        if (descriptor != null) {
            return validateDescriptor(toolClass, descriptor);
        }
        try {
            final Constructor<? extends Tool> constructor = toolClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            final Tool tool = constructor.newInstance();
            return validateDescriptor(toolClass, tool.descriptor());
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(
                    STR."No-arg constructor required for descriptor resolution in \{toolClass.getName()}",
                    exception);
        } catch (InstantiationException
                 | IllegalAccessException
                 | InvocationTargetException exception) {
            throw new IllegalStateException(
                    STR."Failed to instantiate \{toolClass.getName()} for descriptor resolution",
                    exception);
        }
    }

    private static ToolDescriptor resolveDescriptorField(final Class<? extends Tool> toolClass) {
        try {
            final Field descriptorField = toolClass.getDeclaredField("DESCRIPTOR");
            if (!Modifier.isStatic(descriptorField.getModifiers())
                    || !ToolDescriptor.class.isAssignableFrom(descriptorField.getType())) {
                return null;
            }
            descriptorField.setAccessible(true);
            return validateDescriptor(toolClass, (ToolDescriptor) descriptorField.get(null));
        } catch (NoSuchFieldException ignored) {
            return null;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to read descriptor for " + toolClass.getName(), exception);
        }
    }

    private static ToolDescriptor validateDescriptor(
            final Class<? extends Tool> toolClass, final ToolDescriptor descriptor) {
        Objects.requireNonNull(descriptor, STR."Tool descriptor is required for \{toolClass.getName()}");
        if (StringUtils.isBlank(descriptor.name())) {
            throw new IllegalStateException(STR."Tool descriptor name is required for \{toolClass.getName()}");
        }
        return new ToolDescriptor(
                descriptor.name(), descriptor.agentIds(), descriptor.configsSchema());
    }

    private record ToolDefinition(
            ToolDescriptor descriptor,
            Constructor<? extends Tool> constructor,
            List<ConstructorParam> params) {
    }

    private record ConstructorParam(
            String key, Type type, Class<?> rawType, boolean injectContext) {
    }
}
