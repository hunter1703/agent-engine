package com.agentengine.util.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.util.common.beans.UniqueRecord;
import com.agentengine.util.common.testfixtures.Child1;
import com.agentengine.util.common.testfixtures.Child2;
import com.agentengine.util.common.testfixtures.Child3;
import com.agentengine.util.common.testfixtures.ConcreteGreatGrandchild;
import com.agentengine.util.common.testfixtures.InnerChild1;
import com.agentengine.util.common.testfixtures.InnerParent;
import com.agentengine.util.common.testfixtures.Parent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** MongoDB layer coverage: {@link DefaultJsonCodec}, used by {@code SessionEventCodec}. */
class DefaultJsonCodecTest {

  private static DefaultJsonCodec codec() {
    return new DefaultJsonCodec(List.of());
  }

  // Exists only so its generic signature can be captured as a real List<Parent<?>> Type below.
  private static List<Parent<?>> listOfParentSignature() {
    return null;
  }

  private static Type listOfParentType() throws NoSuchMethodException {
    return DefaultJsonCodecTest.class
        .getDeclaredMethod("listOfParentSignature")
        .getGenericReturnType();
  }

  /** {@code Map<String, Object>} values need {@link ObjectTypingModule} to survive. */
  @Test
  void objectDeclaredMapValuesSurviveViaObjectTypingModule() {
    final DefaultJsonCodec codec = codec();
    final Map<String, Object> original = Map.of("value", new Child3(new Child1("nested")));

    final String json = codec.serialize(original, Map.class);
    @SuppressWarnings("unchecked")
    final Map<String, Object> roundTripped =
        (Map<String, Object>) codec.deserialize(json, Map.class);

    assertThat(roundTripped.get("value")).isEqualTo(new Child3(new Child1("nested")));
  }

  /**
   * {@code JAVA_LANG_OBJECT} only tags values whose declared slot is exactly {@code Object} — a
   * {@code List<Parent<?>>}'s elements are declared {@code Parent<?>}, not {@code Object}, so this
   * codec does NOT recover their concrete subtype. Every codec now shares this narrower mode (see
   * {@link ObjectTypingModule}'s javadoc); a genuinely polymorphic type must self-describe at the
   * class level instead. Worth locking in: if this ever starts passing, someone widened the typing
   * mode and every Mongo document just got noisier.
   */
  @Test
  void listElementsDeclaredAsAbstractParentAreNotRecovered() throws Exception {
    final DefaultJsonCodec codec = codec();
    final Type listOfParent = listOfParentType();
    final String json = codec.serialize(List.of(new Child1("a")), listOfParent);

    assertThatThrownBy(() -> codec.deserialize(json, listOfParent))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void nonAbsentInclusionOmitsAbsentFields() {
    final DefaultJsonCodec codec = codec();

    final String json = codec.serialize(new Child1());

    assertThat(json).doesNotContain("value");
  }

  /**
   * An {@code Object}-declared map value that is itself a heterogeneous {@code List}, not a bean.
   */
  @Test
  void objectDeclaredMapValueThatIsItselfAListPreservesEachElement() {
    final DefaultJsonCodec codec = codec();
    final List<Parent<?>> innerList =
        List.of(new Child1("a"), new Child2(1), new Child3(new Child1("nested")));
    final Map<String, Object> original = Map.of("items", innerList);

    final String json = codec.serialize(original, Map.class);
    @SuppressWarnings("unchecked")
    final Map<String, Object> roundTripped =
        (Map<String, Object>) codec.deserialize(json, Map.class);

    assertThat(roundTripped.get("items")).isEqualTo(innerList);
  }

  /** An {@code Object}-declared map value containing a null-element list, and an empty list. */
  @Test
  void objectDeclaredMapValuesHandleNullElementsAndEmptyCollections() {
    final DefaultJsonCodec codec = codec();
    final List<Parent<?>> withNull = new ArrayList<>();
    withNull.add(new Child1("a"));
    withNull.add(null);
    final Map<String, Object> original = Map.of("withNull", withNull, "empty", List.of());

    final String json = codec.serialize(original, Map.class);
    @SuppressWarnings("unchecked")
    final Map<String, Object> roundTripped =
        (Map<String, Object>) codec.deserialize(json, Map.class);

    assertThat(roundTripped.get("withNull")).isEqualTo(withNull);
    assertThat((List<?>) roundTripped.get("empty")).isEmpty();
  }

  /**
   * The same four-level unannotated generic chain exercised in the gRPC layer's {@code
   * GRPCServerImplTest}, nested inside an {@code Object}-declared map value — proves {@code
   * JAVA_LANG_OBJECT} resolves multi-level generic inheritance when the slot is Object-declared.
   */
  @Test
  void objectDeclaredMapValuePreservesDeepGenericChain() {
    final DefaultJsonCodec codec = codec();
    final ConcreteGreatGrandchild original =
        new ConcreteGreatGrandchild(new Child3(new Child1("leaf")), "tag", "extra");
    final Map<String, Object> map = Map.of("chain", original);

    final String json = codec.serialize(map, Map.class);
    @SuppressWarnings("unchecked")
    final Map<String, Object> roundTripped =
        (Map<String, Object>) codec.deserialize(json, Map.class);

    assertThat(roundTripped.get("chain"))
        .isInstanceOf(ConcreteGreatGrandchild.class)
        .isEqualTo(original);
  }

  // Exists only so its generic signature can be captured as a real UniqueRecord<Parent<?>> Type
  // below -- T bound to an abstract, non-wildcard, non-self-describing type (Parent carries no
  // @JsonTypeInfo of its own -- see its javadoc).
  private static UniqueRecord<Parent<?>> uniqueRecordOfParentSignature() {
    return null;
  }

  /**
   * {@code UniqueRecord.record} carries no type discriminator of its own (see its javadoc) -- a
   * fully-resolved, non-self-describing abstract {@code T} loses its concrete subtype. {@code T}
   * must self-describe at the class level for this to work.
   */
  @Test
  void uniqueRecordFieldOfNonSelfDescribingAbstractTypeIsNotRecovered() throws Exception {
    final DefaultJsonCodec codec = codec();
    final Type uniqueRecordOfParent =
        DefaultJsonCodecTest.class
            .getDeclaredMethod("uniqueRecordOfParentSignature")
            .getGenericReturnType();
    final UniqueRecord<Parent<?>> original = new UniqueRecord<>("id-1", new Child1("a"));

    final String json = codec.serialize(original, uniqueRecordOfParent);

    assertThatThrownBy(() -> codec.deserialize(json, uniqueRecordOfParent))
        .isInstanceOf(RuntimeException.class);
  }

  // Exists only so its generic signature can be captured as a real UniqueRecord<InnerParent<?>>
  // Type below -- T bound to a self-describing type (its own class-level @JsonTypeInfo).
  private static UniqueRecord<InnerParent<?>> uniqueRecordOfInnerParentSignature() {
    return null;
  }

  /**
   * A self-describing {@code T} (its own class-level {@code @JsonTypeInfo}) survives on its own.
   */
  @Test
  void uniqueRecordFieldOfSelfDescribingAbstractTypeSurvives() throws Exception {
    final DefaultJsonCodec codec = codec();
    final Type uniqueRecordOfInnerParent =
        DefaultJsonCodecTest.class
            .getDeclaredMethod("uniqueRecordOfInnerParentSignature")
            .getGenericReturnType();
    final UniqueRecord<InnerParent<?>> original =
        new UniqueRecord<>("id-1", new InnerChild1<>("x"));

    final String json = codec.serialize(original, uniqueRecordOfInnerParent);
    final UniqueRecord<?> roundTripped = codec.deserialize(json, uniqueRecordOfInnerParent);

    assertThat(roundTripped.getRecord())
        .isInstanceOf(InnerChild1.class)
        .isEqualTo(new InnerChild1<>("x"));
  }

  /** {@link UniqueRecord} is final -- no class-level type tag, plain deserialization. */
  @Test
  void uniqueRecordWithoutClassTagStillDeserializesAsBase() {
    final DefaultJsonCodec codec = codec();
    final String legacyJson = "{\"id\":\"id-1\",\"record\":\"value\"}";

    final UniqueRecord<?> roundTripped = codec.deserialize(legacyJson, UniqueRecord.class);

    assertThat(roundTripped).isExactlyInstanceOf(UniqueRecord.class);
    assertThat(roundTripped.getRecord()).isEqualTo("value");
  }
}
