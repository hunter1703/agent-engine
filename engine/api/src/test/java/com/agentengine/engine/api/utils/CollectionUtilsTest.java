package com.agentengine.engine.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectionUtilsTest {

  @Test
  void shouldReturnImmutableCopyWhenNullSafeListCalled() {
    final List<String> source = new ArrayList<>(List.of("one", "two"));

    final List<String> copy = CollectionUtils.nullSafeList(source);
    source.add("three");

    assertThat(copy).containsExactly("one", "two");
  }

  @Test
  void shouldAppendVarargsWhenAppendCalledWithItems() {
    final List<String> result = CollectionUtils.append(List.of("a"), List.of("b"), "c", "d");

    assertThat(result).containsExactly("a", "b", "c", "d");
  }

  @Test
  void shouldConvertPrimitiveValuesWhenReadingFromMap() {
    final Map<String, Object> values = Map.of("long", "42", "double", "3.5", "bool", "true");

    assertThat(CollectionUtils.getLongValueFromMap(values, "long")).isEqualTo(42L);
    assertThat(CollectionUtils.getDoubleValueFromMap(values, "double")).isEqualTo(3.5);
    assertThat(CollectionUtils.getBooleanValueFromMap(values, "bool")).isTrue();
  }

  @Test
  void shouldReturnEmptyListWhenListKeyMissing() {
    assertThat(CollectionUtils.getListFromMap(Map.of("x", 1), "missing")).isEmpty();
  }
}
