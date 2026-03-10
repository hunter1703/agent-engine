package com.agentengine.engine.api.tools;

import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.util.StringUtils;
import com.agentengine.engine.api.utils.Utils;
import com.agentengine.engine.api.utils.ToolUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.common.collect.ImmutableMap;
import com.google.genai.types.FunctionDeclaration;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.agentengine.engine.api.utils.Utils.OBJECT_MAPPER;

/**
 * All tools must have one method named "execute". The method can have any parameters.
 */
public abstract class Tool extends BaseTool {
    public static final String ALL = "ALL";

    private static final Logger LOG = LoggerFactory.getLogger(Tool.class);
    private static final String DEFAULT_METHOD = "execute";
    private static final Map<JavaType, Map<String, PropertyBinding>> PROPERTY_BINDINGS_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<Method, List<ParameterBinding>> PARAMETER_BINDINGS_CACHE =
            new ConcurrentHashMap<>();

    private final Method executeMethod;
    private final FunctionDeclaration declaration;
    private final boolean requireConfirmation;
    private final List<ParameterBinding> parameterBindings;
    private final ToolDescriptor toolDescriptor;

    protected Tool(final ToolDescriptor toolDescriptor) {
        this(toolDescriptor, false, false);
    }

    protected Tool(final ToolDescriptor toolDescriptor, final boolean isLongRunning, final boolean requireConfirmation) {
        super(toolDescriptor.name(), toolDescriptor.description(), isLongRunning);
        this.toolDescriptor = toolDescriptor;
        this.executeMethod = getExecuteMethod();
        this.declaration = ToolUtils.buildFunctionDeclaration(executeMethod, toolDescriptor);
        this.requireConfirmation = requireConfirmation;
        this.parameterBindings =
                PARAMETER_BINDINGS_CACHE.computeIfAbsent(executeMethod, Tool::buildParameterBindings);
    }

    public ToolDescriptor descriptor() {
        return toolDescriptor;
    }

    protected Method getExecuteMethod() {
        return Utils.resolveMethod(this.getClass(), DEFAULT_METHOD);
    }

    @Override
    public Optional<FunctionDeclaration> declaration() {
        return Optional.of(declaration);
    }

    @Override
    public Single<Map<String, Object>> runAsync(Map<String, Object> args, ToolContext toolContext) {
        try {
            if (requireConfirmation) {
                if (toolContext.toolConfirmation().isEmpty()) {
                    toolContext.requestConfirmation(
                            String.format(
                                    "Please approve or reject the tool call %s() by responding with a"
                                            + " FunctionResponse with an expected ToolConfirmation payload.",
                                    name()));
                    return Single.just(
                            ImmutableMap.of(
                                    "error", "This tool call requires confirmation, please approve or reject."));
                } else if (!toolContext.toolConfirmation().get().confirmed()) {
                    return Single.just(ImmutableMap.of("error", "This tool call is rejected."));
                }
            }
            return call(CollectionUtils.nullSafeMap(args), toolContext).defaultIfEmpty(ImmutableMap.of());
        } catch (Exception e) {
            LOG.error("Exception occurred while calling function tool: " + executeMethod.getName(), e);
            return Single.just(
                    ImmutableMap.of("status", "error", "message", "An internal error occurred."));
        }
    }

    private Maybe<Map<String, Object>> call(Map<String, Object> args, ToolContext toolContext) throws InvocationTargetException, IllegalAccessException {
        Object[] arguments = buildArguments(args, toolContext);
        Object result = executeMethod.invoke(this, arguments);
        return switch (result) {
            case null -> Maybe.empty();
            case Maybe<?> maybe -> maybe.map(data -> Utils.convertValue(data, new TypeReference<>() {}));
            case Single<?> single -> single.map(data -> Utils.convertValue(data, new TypeReference<Map<String, Object>>() {})).toMaybe();
            default -> {
                try {
                    yield Maybe.just(Utils.convertValue(result, new TypeReference<Map<String, Object>>() {}));
                } catch (IllegalArgumentException e) {
                    yield  Maybe.just(ImmutableMap.of("result", result));
                }
            }
        };
    }

    private Object[] buildArguments(final Map<String, Object> args, final ToolContext toolContext) {
        final Object[] arguments = new Object[parameterBindings.size()];
        for (ParameterBinding binding : parameterBindings) {
            if (binding.isToolContext()) {
                arguments[binding.index()] = toolContext;
                continue;
            }
            if (binding.isInputStream()) {
                arguments[binding.index()] = null;
                continue;
            }
            if (!args.containsKey(binding.name())) {
                if (binding.optional()) {
                    arguments[binding.index()] = null;
                    continue;
                }
                throw new IllegalArgumentException(
                        String.format(
                                "The parameter '%s' was not found in the arguments provided by the model.",
                                binding.name()));
            }
            Object argValue = args.get(binding.name());
            if (binding.isList()) {
                if (argValue instanceof List<?> values) {
                    JavaType elementType = binding.elementType();
                    Class<?> elementClass = elementType == null ? null : elementType.getRawClass();
                    arguments[binding.index()] = createList(values, elementClass, elementType);
                    continue;
                }
            } else if (argValue instanceof Map<?, ?>) {
                arguments[binding.index()] = convertMapValue(argValue, binding.javaType());
                continue;
            }
            arguments[binding.index()] = Utils.castValue(argValue, binding.rawType());
        }
        return arguments;
    }

    private static Object convertMapValue(final Object argValue, final JavaType targetType) {
        final Object normalized =
                targetType.isMapLikeType()
                        ? normalizeMapEntries((Map<?, ?>) argValue, targetType)
                        : normalizeMap((Map<?, ?>) argValue, targetType);
        return Utils.convertValue(normalized, targetType);
    }

    private static Object normalizeValue(final Object value, final JavaType targetType) {
        if (value == null || targetType == null) {
            return value;
        }
        if (value instanceof Map<?, ?> mapValue) {
            if (targetType.isMapLikeType()) {
                return normalizeMapEntries(mapValue, targetType);
            }
            return normalizeMap(mapValue, targetType);
        }
        if (value instanceof List<?> listValue && targetType.isCollectionLikeType()) {
            JavaType contentType = targetType.getContentType();
            List<Object> normalized = new ArrayList<>();
            for (Object entry : listValue) {
                normalized.add(normalizeValue(entry, contentType));
            }
            return normalized;
        }
        return value;
    }

    private static Map<String, Object> normalizeMap(final Map<?, ?> input, final JavaType targetType) {
        Map<String, PropertyBinding> bindings = getPropertyBindings(targetType);
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            String key = entry.getKey() == null ? null : entry.getKey().toString();
            if (key == null) {
                continue;
            }
            Object value = entry.getValue();
            PropertyBinding binding = bindings.get(key);
            if (binding != null) {
                Object mappedValue = normalizeValue(value, binding.type());
                normalized.put(binding.javaName(), mappedValue);
            } else {
                normalized.put(key, value);
            }
        }
        return normalized;
    }

    private static List<Object> createList(List<?> values, Class<?> type, JavaType elementType) {
        List<Object> list = new ArrayList<>();
        if (type == null) {
            return list;
        }
        final boolean simpleType = Utils.isSimpleType(type);
        for (Object value : values) {
            Object normalized = normalizeValue(value, elementType);
            if (simpleType) {
                list.add(Utils.castValue(normalized, type));
            } else if (elementType != null) {
                list.add(Utils.convertValue(normalized, elementType));
            } else {
                list.add(Utils.convertValue(normalized, type));
            }
        }
        return list;
    }

    private static Map<Object, Object> normalizeMapEntries(
            final Map<?, ?> input, final JavaType targetType) {
        final JavaType valueType = targetType.getContentType();
        final Map<Object, Object> normalized = new HashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            Object mappedValue = valueType == null ? value : normalizeValue(value, valueType);
            normalized.put(key, mappedValue);
        }
        return normalized;
    }

    private static List<ParameterBinding> buildParameterBindings(final Method method) {
        final Parameter[] parameters = method.getParameters();
        final List<ParameterBinding> bindings = new ArrayList<>(parameters.length);
        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];
            final String name = ToolUtils.resolveParameterName(parameter);
            final boolean isToolContext = "toolContext".equals(name) || ToolContext.class.isAssignableFrom(parameter.getType());
            final boolean isInputStream = "inputStream".equals(name) || InputStream.class.isAssignableFrom(parameter.getType());
            final ToolSchema schema = parameter.getAnnotation(ToolSchema.class);
            final boolean optional = schema != null && schema.optional();
            final Class<?> rawType = parameter.getType();
            final JavaType javaType = OBJECT_MAPPER.constructType(parameter.getParameterizedType());
            final boolean isList = List.class.equals(rawType);
            final boolean isMap = Map.class.isAssignableFrom(rawType);
            JavaType elementType = null;
            if (isList && parameter.getParameterizedType() instanceof ParameterizedType parameterizedType) {
                final Type element = parameterizedType.getActualTypeArguments()[0];
                elementType = OBJECT_MAPPER.constructType(element);
            }
            bindings.add(new ParameterBinding(i, name, optional, rawType, javaType, elementType, isList, isMap, isToolContext, isInputStream));
        }
        return List.copyOf(bindings);
    }

    private static Map<String, PropertyBinding> getPropertyBindings(final JavaType targetType) {
        return PROPERTY_BINDINGS_CACHE.computeIfAbsent(targetType, Tool::buildPropertyBindings);
    }

    private static Map<String, PropertyBinding> buildPropertyBindings(final JavaType targetType) {
        final BeanDescription beanDescription = OBJECT_MAPPER.getSerializationConfig().introspect(targetType);
        final Map<String, PropertyBinding> bindings = new HashMap<>();
        for (BeanPropertyDefinition property : beanDescription.findProperties()) {
            ToolSchema toolSchema = Utils.findAnnotation(property, ToolSchema.class);
            String javaName = property.getName();
            String schemaName =
                    toolSchema != null && StringUtils.isNotBlank(toolSchema.name())
                            ? toolSchema.name()
                            : javaName;
            final var member = Utils.resolveMember(property);
            if (member == null) {
                continue;
            }
            PropertyBinding binding = new PropertyBinding(javaName, member.getType());
            bindings.put(schemaName, binding);
            if (!schemaName.equals(javaName)) {
                bindings.put(javaName, binding);
            }
        }
        return bindings;
    }

    private record ParameterBinding(int index, String name, boolean optional, Class<?> rawType, JavaType javaType,
                                    JavaType elementType, boolean isList, boolean isMap, boolean isToolContext,
                                    boolean isInputStream) {
    }

    private record PropertyBinding(String javaName, JavaType type) {
    }
}
