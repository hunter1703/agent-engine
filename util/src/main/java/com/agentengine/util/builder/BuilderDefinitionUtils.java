package com.agentengine.util.builder;

import com.agentengine.util.CollectionUtils;
import com.agentengine.util.JsonUtils;
import com.agentengine.util.Secure;
import com.agentengine.util.SchemaUtils;
import com.agentengine.util.builder.annotations.UiAccess;
import com.agentengine.util.builder.annotations.UiAccessLevel;
import com.agentengine.util.builder.annotations.UiBoolean;
import com.agentengine.util.builder.annotations.UiConditionOperator;
import com.agentengine.util.builder.annotations.UiDynamicSchema;
import com.agentengine.util.builder.annotations.UiExpression;
import com.agentengine.util.builder.annotations.UiField;
import com.agentengine.util.builder.annotations.UiGroup;
import com.agentengine.util.builder.annotations.UiLookup;
import com.agentengine.util.builder.annotations.UiNumber;
import com.agentengine.util.builder.annotations.UiPreset;
import com.agentengine.util.builder.annotations.UiRule;
import com.agentengine.util.builder.annotations.UiSelect;
import com.agentengine.util.builder.annotations.UiText;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.json.schema.common.dsl.ObjectSchemaBuilder;
import io.vertx.json.schema.common.dsl.SchemaBuilder;
import io.vertx.json.schema.common.dsl.Schemas;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BuilderDefinitionUtils {

  private BuilderDefinitionUtils() {}

  // ─── Widget types (internal) ──────────────────────────────────────────────

  private enum Widget {
    TEXT, TEXTAREA, SWITCH, NUMBER, SELECT, LOOKUP, DYNAMIC_SCHEMA
  }

  // ─── Public API ───────────────────────────────────────────────────────────

  public static BuilderDefinition generate(final String assetType, final Class<?> rootType) {
    final Context ctx = new Context(rootType);
    final SchemaBuilder<?, ?> schemaBuilder = buildSchemaForType(rootType, ctx, "");
    schemaBuilder.withKeyword("$schema", "https://json-schema.org/draft/2020-12/schema");
    final PresetCollection presets = collectPresets(rootType);
    return new BuilderDefinition(
        toMap(schemaBuilder),
        new UILayout(
            attachFieldPresets(ctx.fields, presets.fieldPresets()),
            presets.rootPresets()));
  }

  public static <T> T sanitize(
      final BuilderDefinition definition,
      final BuilderMode mode,
      final T payload,
      final Class<T> type) {
    if (definition == null || mode == null || payload == null) {
      return payload;
    }
    final Map<String, Object> map = JsonUtils.toMap(payload);
    if (map == null) {
      return payload;
    }
    for (final Map.Entry<String, LayoutField> entry : definition.ui().fields().entrySet()) {
      final String pointer = entry.getKey();
      if (pointer == null || pointer.isBlank() || pointer.indexOf('/', 1) >= 0) {
        continue;
      }
      final UiAccessLevel level = entry.getValue().access().forMode(mode);
      if (level == UiAccessLevel.HIDDEN
          || (mode == BuilderMode.EDIT && level == UiAccessLevel.READ_ONLY)) {
        map.remove(pointer.substring(1));
      }
    }
    return JsonUtils.fromMap(map, type);
  }

  // ─── Schema building ──────────────────────────────────────────────────────

  private static SchemaBuilder<?, ?> buildSchemaForType(
      final Type type, final Context ctx, final String pointer) {
    if (type instanceof ParameterizedType pt) {
      return buildSchemaForParameterized(pt, ctx, pointer);
    }
    if (type instanceof GenericArrayType gat) {
      return Schemas.arraySchema()
          .items(buildSchemaForType(gat.getGenericComponentType(), ctx, pointer + "/*"));
    }
    if (type instanceof WildcardType || type instanceof TypeVariable<?>) {
      return genericObject();
    }
    if (!(type instanceof Class<?> clazz)) {
      return genericObject();
    }
    if (clazz.isEnum()) {
      return enumSchema(clazz);
    }
    if (isStringLike(clazz)) {
      return Schemas.stringSchema();
    }
    if (isIntLike(clazz)) {
      return Schemas.intSchema();
    }
    if (isNumberLike(clazz)) {
      return Schemas.numberSchema();
    }
    if (isBoolLike(clazz)) {
      return Schemas.booleanSchema();
    }
    if (clazz.isArray()) {
      return Schemas.arraySchema()
          .items(buildSchemaForType(clazz.getComponentType(), ctx, pointer + "/*"));
    }
    if (Map.class.isAssignableFrom(clazz)) {
      return Schemas.objectSchema().allowAdditionalProperties(true);
    }
    if (Iterable.class.isAssignableFrom(clazz)) {
      return Schemas.arraySchema().items(genericObject());
    }
    if (Object.class.equals(clazz)) {
      return genericObject();
    }
    if (clazz.getPackageName().startsWith("java.")) {
      return Schemas.stringSchema();
    }
    if (isPolymorphic(clazz)) {
      return buildSchemaForPolymorphic(clazz, ctx, pointer);
    }
    return buildSchemaForObject(clazz, ctx, pointer, null);
  }

  private static SchemaBuilder<?, ?> buildSchemaForParameterized(
      final ParameterizedType pt, final Context ctx, final String pointer) {
    if (!(pt.getRawType() instanceof Class<?> raw)) {
      return genericObject();
    }
    if (Iterable.class.isAssignableFrom(raw)) {
      final Type[] args = pt.getActualTypeArguments();
      return Schemas.arraySchema()
          .items(buildSchemaForType(args.length > 0 ? args[0] : Object.class, ctx, pointer + "/*"));
    }
    if (Map.class.isAssignableFrom(raw)) {
      final Type[] args = pt.getActualTypeArguments();
      return Schemas.objectSchema()
          .additionalProperties(buildSchemaForType(
              args.length > 1 ? args[1] : Object.class, ctx, pointer + "/*"));
    }
    return buildSchemaForType(raw, ctx, pointer);
  }

  private static SchemaBuilder<?, ?> buildSchemaForPolymorphic(
      final Class<?> clazz, final Context ctx, final String pointer) {
    final JsonSubTypes subTypes = clazz.getAnnotation(JsonSubTypes.class);
    if (subTypes == null || subTypes.value().length == 0) {
      return buildSchemaForObject(clazz, ctx, pointer, null);
    }
    final JsonTypeInfo typeInfo = clazz.getAnnotation(JsonTypeInfo.class);
    final String discriminator =
        typeInfo != null && !typeInfo.property().isBlank() ? typeInfo.property() : "type";

    // Collect subtype names upfront (needed for enum on discriminator field)
    final Map<String, Class<?>> subtypesByName = new LinkedHashMap<>();
    for (final JsonSubTypes.Type sub : subTypes.value()) {
      final String name = sub.name() == null || sub.name().isBlank()
          ? sub.value().getSimpleName().toLowerCase(Locale.ROOT) : sub.name();
      subtypesByName.put(name, sub.value());
    }
    final List<String> names = new ArrayList<>(subtypesByName.keySet());

    if (ctx.typeStack.contains(clazz)) {
      return genericObject();
    }
    ctx.typeStack.push(clazz);
    try {
      final ObjectSchemaBuilder schema = Schemas.objectSchema().allowAdditionalProperties(false);
      final Object baseDefaults = instantiate(clazz);
      final Set<String> registered = new LinkedHashSet<>();

      // Step 1: base class fields — common to all subtypes, no auto-rule
      for (final Field field : allFields(clazz)) {
        if (!isSerializable(field)) {
          continue;
        }
        final String name = field.getName();
        final String fp = pointer(pointer, name);
        final SchemaBuilder<?, ?> fs = discriminator.equals(name)
            ? Schemas.stringSchema().withKeyword("enum", names)
            : buildFieldSchema(clazz, field, baseDefaults, ctx, fp);
        final boolean required = discriminator.equals(name) || isRequired(clazz, field);
        if (required) {
          schema.requiredProperty(name, fs);
        } else {
          schema.optionalProperty(name, fs);
        }
        ctx.registerField(fp, clazz, field, toMap(fs), null);
        registered.add(name);
      }

      // Step 2: subtype-unique fields — only visible when discriminator matches
      for (final Map.Entry<String, Class<?>> entry : subtypesByName.entrySet()) {
        final String subtypeName = entry.getKey();
        final Class<?> subtype = entry.getValue();
        final Object subtypeDefaults = instantiate(subtype);
        final Map<String, Object> autoExpr =
            Map.of("===", List.of(Map.of("var", discriminator), subtypeName));
        for (final Field field : subtype.getDeclaredFields()) {
          if (!isSerializable(field) || !registered.add(field.getName())) {
            continue;
          }
          final String fp = pointer(pointer, field.getName());
          final SchemaBuilder<?, ?> fs = buildFieldSchema(subtype, field, subtypeDefaults, ctx, fp);
          schema.optionalProperty(field.getName(), fs);
          ctx.registerField(fp, subtype, field, toMap(fs), autoExpr);
        }
      }

      return schema;
    } finally {
      ctx.typeStack.pop();
    }
  }

  private static SchemaBuilder<?, ?> buildSchemaForObject(
      final Class<?> clazz, final Context ctx, final String pointer,
      final Map<String, Object> autoVisibilityExpr) {
    if (ctx.typeStack.contains(clazz)) {
      return genericObject();
    }
    ctx.typeStack.push(clazz);
    try {
      final ObjectSchemaBuilder schema = Schemas.objectSchema().allowAdditionalProperties(false);
      final Object defaults = instantiate(clazz);
      for (final Field field : allFields(clazz)) {
        if (!isSerializable(field)) {
          continue;
        }
        final String fp = pointer(pointer, field.getName());
        final SchemaBuilder<?, ?> fs = buildFieldSchema(clazz, field, defaults, ctx, fp);
        if (isRequired(clazz, field)) {
          schema.requiredProperty(field.getName(), fs);
        } else {
          schema.optionalProperty(field.getName(), fs);
        }
        ctx.registerField(fp, clazz, field, toMap(fs), autoVisibilityExpr);
      }
      return schema;
    } finally {
      ctx.typeStack.pop();
    }
  }

  /** Builds and decorates a field's schema (type, overrides, writeOnly, default). */
  private static SchemaBuilder<?, ?> buildFieldSchema(
      final Class<?> owner, final Field field, final Object defaults,
      final Context ctx, final String fp) {
    final SchemaBuilder<?, ?> fs = buildSchemaForType(field.getGenericType(), ctx, fp);
    applySchemaOverrides(owner, field, fs);
    if (isAnnotated(owner, field, Secure.class)) {
      fs.withKeyword("writeOnly", true);
    }
    final Object def = readDefault(defaults, field);
    if (isSerializableDefault(def)) {
      fs.defaultValue(def);
    }
    return fs;
  }

  // ─── Field registration ───────────────────────────────────────────────────

  private static void registerField(
      final Context ctx,
      final String pointer,
      final Class<?> owner,
      final Field field,
      final Map<String, Object> fieldSchema,
      final Map<String, Object> autoVisibilityExpr) {
    // Only expose fields that carry at least one builder annotation
    if (!isLayoutField(owner, field)) {
      return;
    }

    final UiField meta = find(owner, field, UiField.class);
    final UiGroup group = owner.getAnnotation(UiGroup.class);
    final Widget widget = detectWidget(owner, field);

    final String label = meta != null && !meta.label().isBlank()
        ? meta.label() : humanize(field.getName());
    final int order = meta != null ? meta.order() : (group != null ? group.order() : 100);
    final boolean advanced = meta != null && meta.advanced();

    final String step = resolveStep(meta, group);
    final String section = resolveSection(meta, group);

    final LayoutAccessPolicy access = buildAccess(find(owner, field, UiAccess.class), field.getName());
    final boolean collection = isCollection(field);

    // Widget-specific fields
    final UiText textAnn = find(owner, field, UiText.class);
    final UiSelect selectAnn = find(owner, field, UiSelect.class);
    final UiLookup lookupAnn = find(owner, field, UiLookup.class);
    final UiDynamicSchema dynamicSchemaAnn = find(owner, field, UiDynamicSchema.class);

    final Boolean multiline = widget == Widget.TEXTAREA ? Boolean.TRUE : null;
    final Integer rows = widget == Widget.TEXTAREA
        ? (textAnn != null && textAnn.rows() > 0 ? textAnn.rows() : 4)
        : null;
    final String numberType = widget == Widget.NUMBER ? inferNumberType(field) : null;
    final List<Object> options = widget == Widget.SELECT
        ? buildSelectOptions(selectAnn, field, fieldSchema) : null;
    final LayoutLookup lookup = widget == Widget.LOOKUP && lookupAnn != null
        ? new LayoutLookup(collection ? Boolean.TRUE : null, lookupAnn.assetType()) : null;
    final LayoutDynamicSchema dynamicSchema = widget == Widget.DYNAMIC_SCHEMA && dynamicSchemaAnn != null
        ? buildDynamicSchema(dynamicSchemaAnn) : null;

    final List<LayoutFieldRule> rules = buildRules(owner, field, autoVisibilityExpr);

    ctx.fields.put(pointer, new LayoutField(
        label,
        widget != null ? widget.name() : null,
        step,
        section,
        order,
        access,
        collection ? Boolean.TRUE : null,
        multiline,
        rows,
        numberType,
        advanced ? Boolean.TRUE : null,
        options == null || options.isEmpty() ? null : options,
        rules.isEmpty() ? null : rules,
        lookup,
        null,
        dynamicSchema,
        isAnnotated(owner, field, Secure.class) ? Boolean.TRUE : null,
        null));

  }

  // ─── Widget detection ─────────────────────────────────────────────────────

  private static boolean isLayoutField(final Class<?> owner, final Field field) {
    return find(owner, field, UiField.class) != null
        || find(owner, field, UiText.class) != null
        || find(owner, field, UiBoolean.class) != null
        || find(owner, field, UiNumber.class) != null
        || find(owner, field, UiSelect.class) != null
        || find(owner, field, UiLookup.class) != null
        || find(owner, field, UiDynamicSchema.class) != null;
  }

  private static Widget detectWidget(final Class<?> owner, final Field field) {
    if (find(owner, field, UiDynamicSchema.class) != null) return Widget.DYNAMIC_SCHEMA;
    if (find(owner, field, UiLookup.class) != null) return Widget.LOOKUP;
    if (find(owner, field, UiSelect.class) != null) return Widget.SELECT;
    if (find(owner, field, UiBoolean.class) != null) return Widget.SWITCH;
    if (find(owner, field, UiNumber.class) != null) return Widget.NUMBER;
    final UiText text = find(owner, field, UiText.class);
    if (text != null) return text.multiline() ? Widget.TEXTAREA : Widget.TEXT;
    return null; // schema-driven
  }

  // ─── Rules ────────────────────────────────────────────────────────────────

  private static List<LayoutFieldRule> buildRules(
      final Class<?> owner, final Field field, final Map<String, Object> autoVisibilityExpr) {
    final UiRule[] uiRules = findAll(owner, field, UiRule.class);
    final UiExpression uiExpr = find(owner, field, UiExpression.class);
    if (uiRules.length == 0 && uiExpr == null && autoVisibilityExpr == null) {
      return List.of();
    }
    // Group by effect, AND expressions within each group. Auto-visibility goes in first.
    final Map<String, List<Map<String, Object>>> byEffect = new LinkedHashMap<>();
    if (autoVisibilityExpr != null) {
      byEffect.computeIfAbsent("VISIBLE", k -> new ArrayList<>()).add(autoVisibilityExpr);
    }
    for (final UiRule rule : uiRules) {
      byEffect.computeIfAbsent(rule.effect().name(), k -> new ArrayList<>())
          .add(toJsonLogic(rule));
    }
    final List<LayoutFieldRule> rules = new ArrayList<>();
    for (final Map.Entry<String, List<Map<String, Object>>> e : byEffect.entrySet()) {
      final List<Map<String, Object>> exprs = e.getValue();
      rules.add(new LayoutFieldRule(e.getKey(),
          exprs.size() == 1 ? exprs.getFirst() : Map.of("and", exprs)));
    }
    if (uiExpr != null) {
      rules.add(new LayoutFieldRule(uiExpr.effect().name(),
          JsonUtils.fromJson(uiExpr.value(), new TypeReference<>() {})));
    }
    return rules;
  }

  private static Map<String, Object> toJsonLogic(final UiRule rule) {
    final List<String> values = Arrays.asList(rule.values());
    return switch (rule.operator()) {
      case EQ -> Map.of("===", List.of(Map.of("var", rule.field()), values.getFirst()));
      case NE -> Map.of("!==", List.of(Map.of("var", rule.field()), values.getFirst()));
      case IN -> Map.of("in", List.of(Map.of("var", rule.field()), values));
      case NOT_IN -> Map.of("!", Map.of("in", List.of(Map.of("var", rule.field()), values)));
    };
  }

  // ─── Access ───────────────────────────────────────────────────────────────

  private static LayoutAccessPolicy buildAccess(final UiAccess ann, final String fieldName) {
    if (ann != null) {
      return new LayoutAccessPolicy(ann.create(), ann.edit(), ann.view());
    }
    if ("id".equals(fieldName)) {
      return new LayoutAccessPolicy(UiAccessLevel.HIDDEN, UiAccessLevel.READ_ONLY, UiAccessLevel.READ_ONLY);
    }
    return new LayoutAccessPolicy(UiAccessLevel.EDITABLE, UiAccessLevel.EDITABLE, UiAccessLevel.READ_ONLY);
  }

  // ─── Select options ───────────────────────────────────────────────────────

  private static List<Object> buildSelectOptions(
      final UiSelect ann, final Field field, final Map<String, Object> fieldSchema) {
    // Explicit enum source
    if (ann != null && !UiSelect.NoEnum.class.equals(ann.enumType())) {
      return enumOptions(ann.enumType());
    }
    // Fall back to schema enum values (covers enum-typed fields)
    final Object enumValues = fieldSchema.get("enum");
    if (enumValues instanceof List<?> list) {
      return new ArrayList<>(list);
    }
    return List.of();
  }

  private static List<Object> enumOptions(final Class<? extends Enum<?>> enumType) {
    final List<Object> options = new ArrayList<>();
    for (final Enum<?> constant : enumType.getEnumConstants()) {
      if ("UNKNOWN".equals(constant.name())) {
        continue;
      }
      final Object value = enumValue(constant);
      if (isValidOption(value)) {
        options.add(value);
      }
    }
    return options;
  }

  private static Object enumValue(final Enum<?> constant) {
    for (final String method : List.of("type", "value", "code", "id")) {
      final Object v = invokeMethod(constant, method);
      if (isValidOption(v)) {
        return v;
      }
    }
    return constant.name().toLowerCase(Locale.ROOT);
  }

  private static boolean isValidOption(final Object value) {
    return value instanceof String s && !s.isBlank()
        || value instanceof Number
        || value instanceof Boolean;
  }

  private static Object invokeMethod(final Enum<?> constant, final String methodName) {
    try {
      final Method m = constant.getDeclaringClass().getMethod(methodName);
      final Class<?> ret = m.getReturnType();
      if (!String.class.equals(ret) && !Number.class.isAssignableFrom(ret)
          && !ret.isPrimitive() && !Boolean.class.equals(ret)) {
        return null;
      }
      return m.invoke(constant);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
      return null;
    }
  }

  // ─── Number type inference ────────────────────────────────────────────────

  private static String inferNumberType(final Field field) {
    final Class<?> type = rawClass(field.getGenericType());
    if (isIntLike(type)) return "integer";
    if (isNumberLike(type)) return "decimal";
    return null;
  }

  // ─── Dynamic schema ───────────────────────────────────────────────────────

  private static LayoutDynamicSchema buildDynamicSchema(final UiDynamicSchema ann) {
    final Map<String, Object> body = new LinkedHashMap<>();
    body.put("assetType", ann.assetType());
    if (!ann.assetIdExpr().isBlank()) body.put("assetId", ann.assetIdExpr());
    if (!ann.contextIdExpr().isBlank()) body.put("contextId", ann.contextIdExpr());
    return new LayoutDynamicSchema("/schemas", "POST", body);
  }

  // ─── Presets ──────────────────────────────────────────────────────────────

  private static PresetCollection collectPresets(final Class<?> rootType) {
    final List<LayoutPreset> rootPresets = new ArrayList<>();
    final Map<String, List<LayoutPreset>> fieldPresets = new LinkedHashMap<>();
    collectPresets(rootType, "", new ArrayDeque<>(), rootPresets, fieldPresets);
    return new PresetCollection(
        rootPresets.isEmpty() ? null : List.copyOf(rootPresets),
        fieldPresets.isEmpty() ? Map.of() : Map.copyOf(fieldPresets));
  }

  private static void collectPresets(
      final Type type,
      final String pointer,
      final Deque<Class<?>> typeStack,
      final List<LayoutPreset> rootPresets,
      final Map<String, List<LayoutPreset>> fieldPresets) {
    if (type instanceof ParameterizedType pt) {
      collectPresetsForParameterized(pt, pointer, typeStack, rootPresets, fieldPresets);
      return;
    }
    if (type instanceof GenericArrayType gat) {
      collectPresets(gat.getGenericComponentType(), pointer + "/*", typeStack, rootPresets, fieldPresets);
      return;
    }
    if (type instanceof WildcardType || type instanceof TypeVariable<?>) {
      return;
    }
    if (!(type instanceof Class<?> clazz)) {
      return;
    }
    if (clazz.isArray()) {
      collectPresets(clazz.getComponentType(), pointer + "/*", typeStack, rootPresets, fieldPresets);
      return;
    }
    if (shouldSkipPresetTraversal(clazz) || typeStack.contains(clazz)) {
      return;
    }

    addPresetsForType(clazz, pointer, rootPresets, fieldPresets);

    if (isPolymorphic(clazz)) {
      final JsonSubTypes subTypes = clazz.getAnnotation(JsonSubTypes.class);
      if (subTypes != null) {
        typeStack.push(clazz);
        try {
          for (final JsonSubTypes.Type subType : subTypes.value()) {
            collectPresets(subType.value(), pointer, typeStack, rootPresets, fieldPresets);
          }
        } finally {
          typeStack.pop();
        }
      }
      return;
    }

    typeStack.push(clazz);
    try {
      for (final Field field : allFields(clazz)) {
        if (!isSerializable(field)) {
          continue;
        }
        collectPresets(
            field.getGenericType(),
            pointer(pointer, field.getName()),
            typeStack,
            rootPresets,
            fieldPresets);
      }
    } finally {
      typeStack.pop();
    }
  }

  private static void collectPresetsForParameterized(
      final ParameterizedType pt,
      final String pointer,
      final Deque<Class<?>> typeStack,
      final List<LayoutPreset> rootPresets,
      final Map<String, List<LayoutPreset>> fieldPresets) {
    if (!(pt.getRawType() instanceof Class<?> raw)) {
      return;
    }
    if (Iterable.class.isAssignableFrom(raw)) {
      final Type[] args = pt.getActualTypeArguments();
      collectPresets(
          args.length > 0 ? args[0] : Object.class,
          pointer + "/*",
          typeStack,
          rootPresets,
          fieldPresets);
      return;
    }
    if (Map.class.isAssignableFrom(raw)) {
      final Type[] args = pt.getActualTypeArguments();
      collectPresets(
          args.length > 1 ? args[1] : Object.class,
          pointer + "/*",
          typeStack,
          rootPresets,
          fieldPresets);
      return;
    }
    collectPresets(raw, pointer, typeStack, rootPresets, fieldPresets);
  }

  private static void addPresetsForType(
      final Class<?> type,
      final String pointer,
      final List<LayoutPreset> rootPresets,
      final Map<String, List<LayoutPreset>> fieldPresets) {
    final List<LayoutPreset> presets = new ArrayList<>();
    for (final UiPreset preset : type.getAnnotationsByType(UiPreset.class)) {
      presets.add(new LayoutPreset(
          preset.id(),
          preset.label(),
          preset.description(),
          preset.isDefault(),
          JsonUtils.fromJson(preset.preset(), new TypeReference<>() {})));
    }
    if (presets.isEmpty()) {
      return;
    }
    if (pointer.isBlank()) {
      rootPresets.addAll(presets);
      return;
    }
    fieldPresets.put(pointer, List.copyOf(presets));
  }

  private static Map<String, LayoutField> attachFieldPresets(
      final Map<String, LayoutField> fields,
      final Map<String, List<LayoutPreset>> fieldPresets) {
    if (fieldPresets.isEmpty()) {
      return fields;
    }
    final Map<String, LayoutField> updatedFields = new LinkedHashMap<>();
    for (final Map.Entry<String, LayoutField> entry : fields.entrySet()) {
      updatedFields.put(
          entry.getKey(),
          entry.getValue().withPresets(fieldPresets.get(entry.getKey())));
    }
    return updatedFields;
  }

  private static boolean shouldSkipPresetTraversal(final Class<?> clazz) {
    return clazz.isEnum()
        || isStringLike(clazz)
        || isIntLike(clazz)
        || isNumberLike(clazz)
        || isBoolLike(clazz)
        || Map.class.isAssignableFrom(clazz)
        || Iterable.class.isAssignableFrom(clazz)
        || Object.class.equals(clazz)
        || clazz.getPackageName().startsWith("java.");
  }

  // ─── Schema helpers ───────────────────────────────────────────────────────

  private static void applySchemaOverrides(
      final Class<?> owner, final Field field, final SchemaBuilder<?, ?> schema) {
    final UiSelect select = find(owner, field, UiSelect.class);
    if (select == null || UiSelect.NoEnum.class.equals(select.enumType())) {
      return;
    }
    schema.withKeyword("enum", enumOptions(select.enumType()));
  }

  private static SchemaBuilder<?, ?> enumSchema(final Class<?> clazz) {
    final List<String> values = new ArrayList<>();
    for (final Object c : clazz.getEnumConstants()) {
      values.add(c.toString());
    }
    return Schemas.stringSchema().withKeyword("enum", values);
  }

  private static SchemaBuilder<?, ?> genericObject() {
    return Schemas.schema().withKeyword("type", "object");
  }

  private static Map<String, Object> toMap(final SchemaBuilder<?, ?> builder) {
    return new LinkedHashMap<>(SchemaUtils.toMap(builder));
  }

  // ─── Reflection helpers ───────────────────────────────────────────────────

  private static <A extends Annotation> A find(
      final Class<?> owner, final Field field, final Class<A> type) {
    final A onField = field.getAnnotation(type);
    if (onField != null) return onField;
    final Method getter = getter(owner, field);
    return getter != null ? getter.getAnnotation(type) : null;
  }

  private static <A extends Annotation> A[] findAll(
      final Class<?> owner, final Field field, final Class<A> type) {
    final A[] onField = field.getAnnotationsByType(type);
    if (onField.length > 0) return onField;
    final Method getter = getter(owner, field);
    if (getter != null) {
      final A[] onGetter = getter.getAnnotationsByType(type);
      if (onGetter.length > 0) return onGetter;
    }
    @SuppressWarnings("unchecked")
    final A[] empty = (A[]) Array.newInstance(type, 0);
    return empty;
  }

  private static boolean isAnnotated(
      final Class<?> owner, final Field field, final Class<? extends Annotation> type) {
    return find(owner, field, type) != null;
  }

  private static Method getter(final Class<?> owner, final Field field) {
    final String name = field.getName();
    final String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    try {
      return owner.getMethod("get" + cap);
    } catch (NoSuchMethodException ignored) {
      try {
        return owner.getMethod("is" + cap);
      } catch (NoSuchMethodException ignoredToo) {
        return null;
      }
    }
  }

  private static List<Field> allFields(final Class<?> type) {
    final Deque<Class<?>> hierarchy = new ArrayDeque<>();
    for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
      hierarchy.push(c);
    }
    final List<Field> fields = new ArrayList<>();
    final Set<String> seen = new LinkedHashSet<>();
    while (!hierarchy.isEmpty()) {
      for (final Field f : hierarchy.pop().getDeclaredFields()) {
        if (seen.add(f.getName())) fields.add(f);
      }
    }
    return fields;
  }

  private static boolean isSerializable(final Field field) {
    final int m = field.getModifiers();
    return !Modifier.isStatic(m) && !Modifier.isTransient(m) && !field.isSynthetic();
  }

  private static boolean isRequired(final Class<?> owner, final Field field) {
    if (isAnnotated(owner, field, NotNull.class) || isAnnotated(owner, field, NotBlank.class)) {
      return true;
    }
    final UiAccess acc = find(owner, field, UiAccess.class);
    return acc != null && (acc.create() == UiAccessLevel.REQUIRED || acc.edit() == UiAccessLevel.REQUIRED);
  }

  private static boolean isCollection(final Field field) {
    final Class<?> type = field.getType();
    return Iterable.class.isAssignableFrom(type) || type.isArray();
  }

  private static boolean isPolymorphic(final Class<?> clazz) {
    return clazz.getAnnotation(JsonSubTypes.class) != null
        && clazz.getAnnotation(JsonTypeInfo.class) != null;
  }

  private static boolean isStringLike(final Class<?> c) {
    return String.class.equals(c) || CharSequence.class.isAssignableFrom(c)
        || c.getPackageName().startsWith("java.time");
  }

  private static boolean isIntLike(final Class<?> c) {
    return c == int.class || c == Integer.class || c == long.class || c == Long.class
        || c == short.class || c == Short.class || c == byte.class || c == Byte.class;
  }

  private static boolean isNumberLike(final Class<?> c) {
    return c == double.class || c == Double.class || c == float.class || c == Float.class;
  }

  private static boolean isBoolLike(final Class<?> c) {
    return c == boolean.class || c == Boolean.class;
  }

  private static Object instantiate(final Class<?> clazz) {
    try {
      final var ctor = clazz.getDeclaredConstructor();
      ctor.setAccessible(true);
      return ctor.newInstance();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Object readDefault(final Object instance, final Field field) {
    if (instance == null) return null;
    try {
      field.setAccessible(true);
      return field.get(instance);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static boolean isSerializableDefault(final Object value) {
    if (value == null) return false;
    if (value instanceof String s) return !s.isBlank();
    if (value instanceof List<?> l) return !l.isEmpty();
    if (value instanceof Map<?, ?> m) return !m.isEmpty();
    return value instanceof Number || value instanceof Boolean || value.getClass().isEnum();
  }

  private static Class<?> rawClass(final Type type) {
    if (type instanceof Class<?> c) return c;
    if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> c) return c;
    if (type instanceof GenericArrayType gat) {
      return Array.newInstance(rawClass(gat.getGenericComponentType()), 0).getClass();
    }
    return Object.class;
  }

  // ─── String helpers ───────────────────────────────────────────────────────

  private static String resolveStep(final UiField meta, final UiGroup group) {
    if (meta != null && !meta.step().isBlank()) return meta.step();
    if (group != null && !group.step().isBlank()) return group.step();
    return "general";
  }

  private static String resolveSection(final UiField meta, final UiGroup group) {
    if (meta != null && !meta.section().isBlank()) return meta.section();
    if (group != null && !group.section().isBlank()) return group.section();
    return "general";
  }

  private static String pointer(final String parent, final String name) {
    final String escaped = name.replace("~", "~0").replace("/", "~1");
    return parent.isBlank() ? "/" + escaped : parent + "/" + escaped;
  }

  private static String humanize(final String fieldName) {
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < fieldName.length(); i++) {
      final char c = fieldName.charAt(i);
      if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(fieldName.charAt(i - 1))) {
        sb.append(' ');
      }
      sb.append(c == '_' ? ' ' : c);
    }
    return toTitle(sb.toString());
  }

  private static String toTitle(final String value) {
    if (value == null || value.isBlank()) return "Field";
    final StringBuilder sb = new StringBuilder();
    for (final String word : value.replace("_", " ").replace('-', ' ').split("\\s+")) {
      if (word.isBlank()) continue;
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return sb.toString();
  }

  // ─── Context ──────────────────────────────────────────────────────────────

  private static final class Context {
    final Class<?> rootType;
    final Deque<Class<?>> typeStack = new ArrayDeque<>();
    final Map<String, LayoutField> fields = new LinkedHashMap<>();

    Context(final Class<?> rootType) {
      this.rootType = rootType;
    }

    void registerField(
        final String pointer, final Class<?> owner,
        final Field field, final Map<String, Object> fieldSchema,
        final Map<String, Object> autoVisibilityExpr) {
      BuilderDefinitionUtils.registerField(this, pointer, owner, field, fieldSchema, autoVisibilityExpr);
    }
  }

  private record PresetCollection(
      List<LayoutPreset> rootPresets,
      Map<String, List<LayoutPreset>> fieldPresets) {}
}
