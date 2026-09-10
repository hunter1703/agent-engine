package com.agentengine.util.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link JsonCodec#splitJsonArray} is a hand-rolled JSON-aware scan (no tree parse, no
 * re-serialize) with no prior test coverage -- these cover the string/escape/nesting edge cases a
 * naive comma-split would get wrong, verified by round-tripping each split element back through the
 * codec rather than comparing exact strings (robust to whitespace normalization).
 */
class JsonCodecTest {

  private static DefaultJsonCodec codec() {
    return new DefaultJsonCodec(List.of());
  }

  @Test
  void blankInputReturnsEmptyList() {
    assertThat(codec().splitJsonArray("")).isEmpty();
    assertThat(codec().splitJsonArray("   ")).isEmpty();
    assertThat(codec().splitJsonArray(null)).isEmpty();
  }

  @Test
  void nonArrayInputReturnsItselfAsTheOnlyElement() {
    final DefaultJsonCodec codec = codec();
    final String json = codec.serialize(Map.of("a", 1));

    assertThat(codec.splitJsonArray(json)).containsExactly(json);
  }

  @Test
  void emptyArrayReturnsEmptyList() {
    assertThat(codec().splitJsonArray("[]")).isEmpty();
    assertThat(codec().splitJsonArray("[ ]")).isEmpty();
  }

  @Test
  void singleScalarElement() {
    assertThat(codec().splitJsonArray("[1]")).containsExactly("1");
  }

  @Test
  void multipleScalarElementsOfMixedTypes() {
    assertThat(codec().splitJsonArray("[1,2.5,true,false,null,\"x\"]"))
        .containsExactly("1", "2.5", "true", "false", "null", "\"x\"");
  }

  @Test
  void whitespaceAroundElementsIsTrimmed() {
    assertThat(codec().splitJsonArray("[ 1 , 2 , 3 ]")).containsExactly("1", "2", "3");
    assertThat(codec().splitJsonArray("[\n  1,\n  2\n]")).containsExactly("1", "2");
  }

  @Test
  void commaInsideAStringDoesNotSplitTheElement() {
    assertThat(codec().splitJsonArray("[\"a,b\",\"c,d\"]")).containsExactly("\"a,b\"", "\"c,d\"");
  }

  @Test
  void escapedQuoteInsideAStringDoesNotEndTheString() {
    // JSON text: ["a\"b"] -- one string element whose content is: a"b
    final String json = "[\"a\\\"b\"]";
    assertThat(codec().splitJsonArray(json)).containsExactly("\"a\\\"b\"");
  }

  @Test
  void escapedBackslashFollowedByQuoteEndsTheString() {
    // JSON text: ["a\\"] -- string content is a single trailing backslash: a\
    // The \\ is an escaped backslash, so the following " is NOT escaped and DOES end the string.
    final String json = "[\"a\\\\\"]";
    final DefaultJsonCodec codec = codec();

    final List<String> parts = codec.splitJsonArray(json);

    assertThat(parts).hasSize(1);
    // Round-trip through the codec to confirm the string content is exactly one backslash,
    // proving the scanner didn't mis-treat \\" as an escaped quote and run past the real end.
    assertThat(codec.deserialize(parts.get(0), String.class)).isEqualTo("a\\");
  }

  @Test
  void bracesAndBracketsInsideAStringAreNotStructural() {
    final String json = "[\"{not real}\",\"[not real]\"]";
    assertThat(codec().splitJsonArray(json)).containsExactly("\"{not real}\"", "\"[not real]\"");
  }

  @Test
  void unicodeEscapeLookingLikeStructureIsNotMisread() {
    // \u007B decodes to '{' but must never be treated as structural while still escaped text.
    final String json = "[\"\\u007B\",\"tail\"]";
    final DefaultJsonCodec codec = codec();

    final List<String> parts = codec.splitJsonArray(json);

    assertThat(parts).hasSize(2);
    assertThat(codec.deserialize(parts.get(0), String.class)).isEqualTo("{");
    assertThat(codec.deserialize(parts.get(1), String.class)).isEqualTo("tail");
  }

  @Test
  void nestedObjectsAreNotSplitOnTheirOwnCommas() {
    final DefaultJsonCodec codec = codec();
    final String json = codec.serialize(List.of(Map.of("a", 1, "b", 2), Map.of("c", 3)));

    final List<String> parts = codec.splitJsonArray(json);

    assertThat(parts).hasSize(2);
    assertThat(codec.deserialize(parts.get(0), Map.class)).isEqualTo(Map.of("a", 1, "b", 2));
    assertThat(codec.deserialize(parts.get(1), Map.class)).isEqualTo(Map.of("c", 3));
  }

  @Test
  void nestedArraysAreNotSplitOnTheirOwnCommas() {
    final String json = "[[1,2],[3,4,5]]";

    assertThat(codec().splitJsonArray(json)).containsExactly("[1,2]", "[3,4,5]");
  }

  @Test
  void deeplyNestedMixedStructureRoundTripsElementByElement() {
    final DefaultJsonCodec codec = codec();
    final Map<String, Object> first =
        Map.of("type", "A", "value", Map.of("nested", List.of(1, 2, Map.of("x", "y,z"))));
    final Map<String, Object> second = Map.of("type", "B");
    final String json = codec.serialize(List.of(first, second));

    final List<String> parts = codec.splitJsonArray(json);

    assertThat(parts).hasSize(2);
    assertThat(codec.deserialize(parts.get(0), Map.class)).isEqualTo(first);
    assertThat(codec.deserialize(parts.get(1), Map.class)).isEqualTo(second);
  }

  @Test
  void malformedArrayWithNoClosingBracketThrows() {
    assertThatThrownBy(() -> codec().splitJsonArray("[1,2")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void elementsPreserveOrderForALargerBatch() {
    final DefaultJsonCodec codec = codec();
    final List<Integer> original = List.of(5, 4, 3, 2, 1, 0, -1);
    final String json = codec.serialize(original);

    final List<String> parts = codec.splitJsonArray(json);

    assertThat(parts).hasSize(original.size());
    for (int i = 0; i < original.size(); i++) {
      assertThat(codec.deserialize(parts.get(i), Integer.class)).isEqualTo(original.get(i));
    }
  }
}
