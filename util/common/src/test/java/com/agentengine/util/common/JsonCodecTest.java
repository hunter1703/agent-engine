package com.agentengine.util.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@link JsonCodec#splitJsonArray} uses Jackson's own {@link com.fasterxml.jackson.core.JsonParser}
 * for token boundaries (no tree parse, no re-serialize) with no prior test coverage -- these cover
 * the string/escape/nesting edge cases a naive comma-split would get wrong, verified by
 * round-tripping each split element back through the codec rather than comparing exact strings
 * (robust to whitespace normalization).
 */
class JsonCodecTest {

  private static DefaultJsonCodec codec() {
    return new DefaultJsonCodec(List.of());
  }

  /**
   * Strips the {@code @class} discriminator {@link ObjectTypingModule} deliberately stamps on every
   * ambiguous {@code Object}-declared slot (see its javadoc -- needed for real payloads like {@code
   * FunctionCall.args()}) -- recursively, since a value nested inside a {@code Map.of(...)}/{@code
   * List.of(...)} built here is itself an {@code Object}-erased slot and gets its own stamp. These
   * tests exist to verify {@code splitJsonArray}'s element-boundary detection, not full round-trip
   * fidelity through default typing, so the discriminator is irrelevant to what they assert.
   */
  @SuppressWarnings("unchecked")
  private static Object stripClassDiscriminator(final Object value) {
    if (value instanceof Map<?, ?> map) {
      final Map<String, Object> cleaned = new java.util.LinkedHashMap<>();
      map.forEach(
          (key, v) -> {
            if (!"@class".equals(key)) {
              cleaned.put((String) key, stripClassDiscriminator(v));
            }
          });
      return cleaned;
    }
    if (value instanceof List<?> list) {
      return list.stream().map(JsonCodecTest::stripClassDiscriminator).toList();
    }
    return value;
  }

  @Test
  void blankInputReturnsEmptyList() {
    assertThat(codec().splitJsonArray("")).isEmpty();
    assertThat(codec().splitJsonArray("   ")).isEmpty();
    assertThat(codec().splitJsonArray((String) null)).isEmpty();
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
    assertThat(stripClassDiscriminator(codec.deserialize(parts.get(0), Map.class)))
        .isEqualTo(Map.of("a", 1, "b", 2));
    assertThat(stripClassDiscriminator(codec.deserialize(parts.get(1), Map.class)))
        .isEqualTo(Map.of("c", 3));
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
    assertThat(stripClassDiscriminator(codec.deserialize(parts.get(0), Map.class)))
        .isEqualTo(first);
    assertThat(stripClassDiscriminator(codec.deserialize(parts.get(1), Map.class)))
        .isEqualTo(second);
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

  /**
   * {@link JsonCodec#splitJsonArray(byte[])} slices by {@link
   * com.fasterxml.jackson.core.JsonLocation#getByteOffset()} instead of {@code getCharOffset()} --
   * covers the same edge cases as the {@code String} overload above (escapes, nesting, multi-byte
   * UTF-8 content) via direct equivalence against it, since both must agree on element boundaries
   * for the same logical input.
   */
  @ParameterizedTest
  @MethodSource("splitJsonArrayInputs")
  void byteArrayOverloadMatchesStringOverload(final String json) {
    final DefaultJsonCodec codec = codec();

    final List<String> fromString = codec.splitJsonArray(json);
    final List<byte[]> fromBytes = codec.splitJsonArray(json.getBytes(StandardCharsets.UTF_8));

    assertThat(fromBytes.stream().map(b -> new String(b, StandardCharsets.UTF_8)).toList())
        .containsExactlyElementsOf(fromString);
  }

  private static Stream<String> splitJsonArrayInputs() {
    return Stream.of(
        "[]",
        "[1]",
        "[1,2.5,true,false,null,\"x\"]",
        "[ 1 , 2 , 3 ]",
        "[\"a,b\",\"c,d\"]",
        "[\"a\\\"b\"]",
        "[\"a\\\\\"]",
        "[\"{not real}\",\"[not real]\"]",
        "[\"\\u007B\",\"tail\"]",
        "[[1,2],[3,4,5]]",
        // multi-byte UTF-8 content (emoji, accented characters) -- byte offsets must still land
        // on the correct element boundaries even though byte length differs from char length.
        "[\"caf\\u00e9\",\"\\ud83d\\ude00 party\",\"tail\"]");
  }

  @Test
  void byteArrayOverloadHandlesBlankAndEmptyInput() {
    assertThat(codec().splitJsonArray((byte[]) null)).isEmpty();
    assertThat(codec().splitJsonArray(new byte[0])).isEmpty();
  }

  @Test
  void byteArrayOverloadNonArrayInputReturnsItselfAsTheOnlyElement() {
    final DefaultJsonCodec codec = codec();
    final byte[] json = codec.serialize(Map.of("a", 1)).getBytes(StandardCharsets.UTF_8);

    final List<byte[]> parts = codec.splitJsonArray(json);

    assertThat(parts).hasSize(1);
    assertThat(parts.get(0)).isEqualTo(json);
  }
}
