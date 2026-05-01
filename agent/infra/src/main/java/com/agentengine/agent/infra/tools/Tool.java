package com.agentengine.agent.infra.tools;

import com.agentengine.agent.infra.utils.*;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.beans.tools.ToolRiskLevel;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.ExceptionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Utils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** All tools must have one method named "execute". The method can have any parameters. */
public abstract class Tool extends BaseTool {

    private static final Logger LOG = LoggerFactory.getLogger(Tool.class);
    private static final String DEFAULT_METHOD = "execute";
    private static final Map<JavaType, Map<String, PropertyBinding>> PROPERTY_BINDINGS_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<Method, List<ParameterBinding>> PARAMETER_BINDINGS_CACHE = new ConcurrentHashMap<>();

    private final Method executeMethod;
    private final FunctionDeclaration declaration;
    private final List<ParameterBinding> parameterBindings;
    private final ToolDescriptor toolDescriptor;

    protected Tool(final ToolDescriptor toolDescriptor) {
        this(toolDescriptor, false);
    }

    protected Tool(final ToolDescriptor toolDescriptor, final boolean isLongRunning) {
        super(toolDescriptor.name(), toolDescriptor.description(), isLongRunning);
        this.toolDescriptor = toolDescriptor;
        this.executeMethod = getExecuteMethod();
        this.declaration = ToolUtils.buildFunctionDeclaration(executeMethod, toolDescriptor);
        LOG.info("descriptor : {}, declaration : {}", JsonUtils.toJson(toolDescriptor), JsonUtils.toJson(declaration));
        this.parameterBindings = PARAMETER_BINDINGS_CACHE.computeIfAbsent(executeMethod, Tool::buildParameterBindings);
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
            final ToolRiskLevel toolRiskLevel = descriptor().riskLevel();
            if (toolRiskLevel == ToolRiskLevel.HIGH || toolRiskLevel == ToolRiskLevel.CRITICAL) {
                if (toolContext.toolConfirmation().isEmpty()) {
                    toolContext.requestConfirmation(
                            String.format(
                                    "Please approve or reject the tool call %s() by responding with a"
                                            + " FunctionResponse with an expected ToolConfirmation payload.",
                                    name()),
                            args);
                    return Single.just(ImmutableMap.of(
                            "error", "This tool call requires confirmation, please approve or reject."));
                } else if (!toolContext.toolConfirmation().get().confirmed()) {
                    return Single.just(ImmutableMap.of("error", "This tool call is rejected."));
                }
            }
            // Tools that call toolContext.requestConfirmation() should return empty map or null.
            // ADK emits both confirmation event and function response event. BaseFlow filters out
            // empty function responses when confirmation is requested, preventing placeholder results
            // from reaching the LLM. On resume, tool is called again and returns actual result.
            return call(CollectionUtils.nullSafeMap(args), toolContext).defaultIfEmpty(ImmutableMap.of());
        } catch (Exception exception) {
            LOG.error("Exception occurred while calling function tool: {}", executeMethod.getName(), exception);
            final Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            return Single.just(ImmutableMap.of("status", "error", "message", cause.getMessage()));
        }
    }

    private Maybe<Map<String, Object>> call(Map<String, Object> args, ToolContext toolContext)
            throws InvocationTargetException, IllegalAccessException {
        final Object[] arguments = buildArguments(args, toolContext);
        final ToolOutput<?> result = (ToolOutput<?>) executeMethod.invoke(this, arguments);
        if (result == null || result.isEmpty()) {
            return Maybe.empty();
        }
        if (result instanceof ToolOutput.Knowledge knowledge) {
            final RunState runState = RunUtils.getOrInitState(toolContext.invocationContext());
            runState.addReminder(new Reminder(
                    Reminder.GROUP_INDEXED_KNOWLEDGE,
                    knowledge.getKnowledgeId(),
                    "knowledgeId='" + knowledge.getKnowledgeId() + "' (tool: " + name() + ") — "
                            + knowledge.getHint()));
        }
        return Maybe.just(Utils.convertValue(result.toResult(), new TypeReference<>() {}));
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
                // Some models flatten a single complex-type parameter's fields directly to
                // the top-level args map instead of nesting them under the parameter name.
                // When the parameter is a complex object and args is non-empty, attempt to
                // convert args itself as the parameter value before failing.
                if (!binding.isList()
                        && !binding.isMap()
                        && !Utils.isSimpleType(binding.rawType())
                        && !args.isEmpty()) {
                    try {
                        arguments[binding.index()] = convertMapValue(args, binding.javaType());
                        continue;
                    } catch (Exception ignored) {
                        // fall through to the standard error
                    }
                }
                throw new IllegalArgumentException(String.format(
                        "The parameter '%s' was not found in the arguments provided by the model.", binding.name()));
            }
            Object argValue = args.get(binding.name());

            // Handle case where LLM passes JSON string for complex types (common with some models like llama3.1)
            // If value is a string but expected type is not a simple/primitive type, parse as JSON
            if (argValue instanceof String stringValue && !Utils.isSimpleType(binding.rawType())) {
                try {
                    argValue = JsonUtils.fromJson(stringValue, binding.javaType());
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Failed to parse JSON string for parameter '%s': %s",
                                    binding.name(), e.getMessage()),
                            e);
                }
            }

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
        final Object normalized = targetType.isMapLikeType()
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

    private static Map<Object, Object> normalizeMapEntries(final Map<?, ?> input, final JavaType targetType) {
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
        for (int index = 0; index < parameters.length; index++) {
            final Parameter parameter = parameters[index];
            final String name = ToolUtils.resolveParameterName(parameter);
            final boolean isToolContext =
                    "toolContext".equals(name) || ToolContext.class.isAssignableFrom(parameter.getType());
            final boolean isInputStream =
                    "inputStream".equals(name) || InputStream.class.isAssignableFrom(parameter.getType());
            final ToolSchema schema = parameter.getAnnotation(ToolSchema.class);
            final boolean optional = schema != null && schema.optional();
            final Class<?> rawType = parameter.getType();
            final JavaType javaType = Utils.constructType(parameter.getParameterizedType());
            final boolean isList = List.class.equals(rawType);
            final boolean isMap = Map.class.isAssignableFrom(rawType);
            JavaType elementType = null;
            if (isList && parameter.getParameterizedType() instanceof ParameterizedType parameterizedType) {
                final Type element = parameterizedType.getActualTypeArguments()[0];
                elementType = Utils.constructType(element);
            }
            bindings.add(new ParameterBinding(
                    index,
                    name,
                    optional,
                    rawType,
                    javaType,
                    elementType,
                    isList,
                    isMap,
                    isToolContext,
                    isInputStream));
        }
        return List.copyOf(bindings);
    }

    private static Map<String, PropertyBinding> getPropertyBindings(final JavaType targetType) {
        return PROPERTY_BINDINGS_CACHE.computeIfAbsent(targetType, Tool::buildPropertyBindings);
    }

    private static Map<String, PropertyBinding> buildPropertyBindings(final JavaType targetType) {
        final BeanDescription beanDescription = Utils.getBeanDescription(targetType);
        final Map<String, PropertyBinding> bindings = new HashMap<>();
        for (BeanPropertyDefinition property : beanDescription.findProperties()) {
            ToolSchema toolSchema = Utils.findAnnotation(property, ToolSchema.class);
            String javaName = property.getName();
            String schemaName =
                    toolSchema != null && StringUtils.isNotBlank(toolSchema.name()) ? toolSchema.name() : javaName;
            final AnnotatedMember member = Utils.resolveMember(property);
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

    private record ParameterBinding(
            int index,
            String name,
            boolean optional,
            Class<?> rawType,
            JavaType javaType,
            JavaType elementType,
            boolean isList,
            boolean isMap,
            boolean isToolContext,
            boolean isInputStream) {}

    private record PropertyBinding(String javaName, JavaType type) {}
}
