package com.agentengine.util.common.builder;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.builder.annotations.UiAccessLevel;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuilderDefinition(Map<String, Object> schema, UILayout layout) {

  public BuilderDefinition resolve(final BuilderMode mode) {
    if (mode == null) {
      return this;
    }
    final Map<String, LayoutField> resolvedFields = new LinkedHashMap<>();
    final List<String> hiddenPointers = new ArrayList<>();
    for (final Map.Entry<String, LayoutField> entry : layout.fields().entrySet()) {
      final String pointer = entry.getKey();
      final LayoutAccessPolicy policy = entry.getValue().access();
      final UiAccessLevel access = policy == null ? UiAccessLevel.EDITABLE : policy.forMode(mode);
      if (access == UiAccessLevel.HIDDEN) {
        hiddenPointers.add(pointer);
        continue;
      }
      resolvedFields.put(pointer, entry.getValue().withCurrentAccess(access.name()));
    }

    final Map<String, Object> resolvedSchema = deepCopySchema(schema);
    for (final String pointer : hiddenPointers) {
      removeHiddenPropertyFromSchema(resolvedSchema, pointer);
    }

    return new BuilderDefinition(
        resolvedSchema,
        new UILayout(
            new LinkedHashMap<>(resolvedFields),
            layout.presets() == null || layout.presets().isEmpty()
                ? null
                : List.copyOf(layout.presets()),
            layout.steps()));
  }

  private static Map<String, Object> deepCopySchema(final Map<String, Object> input) {
    final String json = JsonUtils.toStableJson(input);
    return JsonUtils.fromJson(json, new TypeReference<>() {});
  }

  private static void removeHiddenPropertyFromSchema(
      final Map<String, Object> resolved, final String pointer) {
    if (pointer == null || pointer.isBlank() || pointer.indexOf('/', 1) >= 0) {
      return;
    }
    final String property = pointer.substring(1);
    removePropertyRecursive(resolved, property);
  }

  private static void removePropertyRecursive(
      final Map<String, Object> node, final String property) {
    if (node == null || property == null) {
      return;
    }
    final Map<String, Object> properties = CollectionUtils.getMapFromMap(node, "properties");
    if (properties != null) {
      properties.remove(property);
    }
    final List<String> required = CollectionUtils.getListFromMap(node, "required");
    if (required != null) {
      required.remove(property);
    }
    recurseArray(CollectionUtils.getValueFromMap(node, "oneOf"), property);
    recurseArray(CollectionUtils.getValueFromMap(node, "allOf"), property);
    recurseArray(CollectionUtils.getValueFromMap(node, "anyOf"), property);
    recurseObject(CollectionUtils.getValueFromMap(node, "items"), property);
  }

  private static void recurseArray(final Object value, final String property) {
    if (!(value instanceof List<?> list)) {
      return;
    }
    for (final Object element : list) {
      recurseObject(element, property);
    }
  }

  private static void recurseObject(final Object value, final String property) {
    if (!(value instanceof Map<?, ?> rawMap)) {
      return;
    }
    @SuppressWarnings("unchecked")
    final Map<String, Object> valueMap = (Map<String, Object>) rawMap;
    removePropertyRecursive(valueMap, property);
  }
}
