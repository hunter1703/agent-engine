package com.localagent.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CollectionUtilsTest {

    @Test
    void nullSafeListReturnsImmutableCopy() {
        List<String> input = new ArrayList<>(List.of("a", "b"));

        List<String> result = CollectionUtils.nullSafeList(input);

        assertThat(result).containsExactly("a", "b");
        assertThatThrownBy(() -> result.add("c")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullSafeMutableListReturnsMutableCopy() {
        List<String> input = new ArrayList<>(List.of("a"));

        List<String> result = CollectionUtils.nullSafeMutableList(input);
        result.add("b");

        assertThat(result).containsExactly("a", "b");
    }

    @Test
    void getStringValueFromMapReturnsNullWhenMissing() {
        assertThat(CollectionUtils.getStringValueFromMap(null, "key")).isNull();
        assertThat(CollectionUtils.getStringValueFromMap(Map.of(), "key")).isNull();
    }

    @Test
    void getLongValueFromMapHandlesNumbersAndStrings() {
        Map<String, Object> values = new HashMap<>();
        values.put("num", 5L);
        values.put("str", "7");

        assertThat(CollectionUtils.getLongValueFromMap(values, "num")).isEqualTo(5L);
        assertThat(CollectionUtils.getLongValueFromMap(values, "str")).isEqualTo(7L);
    }

    @Test
    void transformHelpersSkipNullKeysAndMapMultipleKeys() {
        record Item(String value, List<String> keys) {}

        List<Item> items = List.of(new Item("first", List.of("a", "b")), new Item("skip", List.of()));

        Map<String, String> multiKey = CollectionUtils.transformToMultiKeyMap(items, Item::keys, Item::value);
        Map<String, String> singleKey = CollectionUtils.transformToMap(items, item -> item.keys().isEmpty() ? null : item.keys().getFirst(), Item::value);

        assertThat(multiKey).containsEntry("a", "first").containsEntry("b", "first");
        assertThat(singleKey).containsEntry("a", "first");
    }
}
