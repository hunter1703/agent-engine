package com.agentengine.engine.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionUtilsTest {

  @Test
  void nullSafeMutableListReturnsMutableList() {
    final List<String> list = CollectionUtils.nullSafeMutableList(null);

    list.add("value");

    assertThat(list).containsExactly("value");
  }

  @Test
  void nullSafeMutableSetReturnsMutableSet() {
    final Set<String> set = CollectionUtils.nullSafeMutableSet(null);

    set.add("value");

    assertThat(set).containsExactly("value");
  }

  @Test
  void getLongValueFromMapHandlesNullValue() {
    final HashMap<String, Object> map = new HashMap<>();
    map.put("count", null);

    assertThat(CollectionUtils.getLongValueFromMap(map, "count")).isNull();
  }
}
