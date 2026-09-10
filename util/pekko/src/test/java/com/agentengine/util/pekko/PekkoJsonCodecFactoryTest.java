package com.agentengine.util.pekko;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.events.SequencedEvent;
import com.agentengine.util.common.testfixtures.Child1;
import com.agentengine.util.common.testfixtures.Child2;
import com.agentengine.util.common.testfixtures.Child3;
import com.agentengine.util.common.testfixtures.Child4;
import com.agentengine.util.common.testfixtures.ConcreteGreatGrandchild;
import com.agentengine.util.common.testfixtures.InnerChild1;
import com.agentengine.util.common.testfixtures.Parent;
import com.agentengine.util.pekko.PekkoJsonCodecFactory.PekkoJsonCodec;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pekko-layer codec coverage: both ends of this wire format are always our own services (see {@link
 * com.agentengine.util.common.AllowAllPolymorphicTypeValidator}), and it's used for cluster
 * messages and event-sourced persistence via {@code jackson-cbor}.
 */
class PekkoJsonCodecFactoryTest {

  private static PekkoJsonCodecFactory factory() {
    return new PekkoJsonCodecFactory(new PekkoJsonCodec(List.of()));
  }

  /**
   * {@code Map<String, Object>} values need {@link com.agentengine.util.common.ObjectTypingModule}
   * to survive — the exact mechanism that fixes ADK's {@code FunctionCall}-in-a-confirmation-map
   * shape, exercised here generically instead of pulling in agent/core's ADK-specific types.
   */
  @Test
  void objectDeclaredMapValuesSurviveViaObjectTypingModule() throws Exception {
    final ObjectMapper mapper = factory().newObjectMapper("test", new JsonFactory());

    final Map<String, Object> original = Map.of("original", new Child3(new Child1("nested")));
    final String json =
        mapper.writerFor(mapper.constructType(Map.class)).writeValueAsString(original);

    @SuppressWarnings("unchecked")
    final Map<String, Object> roundTripped = mapper.readValue(json, Map.class);

    assertThat(roundTripped.get("original")).isEqualTo(new Child3(new Child1("nested")));
  }

  /** Matches the other three codecs' base mapper — see {@code JsonCodec}/{@code JsonUtils}. */
  @Test
  void nonAbsentInclusionOmitsAbsentFields() throws Exception {
    final ObjectMapper mapper = factory().newObjectMapper("test", new JsonFactory());

    final String json = mapper.writeValueAsString(new Child1());

    assertThat(json).doesNotContain("value");
  }

  /**
   * {@code SequencedEvent} carries no type discriminator of its own (see its javadoc) -- Pekko's
   * actual wire path ({@code JacksonSerializer.toBinary/fromBinary}) always calls the mapper with
   * the raw runtime class, never a resolved {@code SequencedEvent<Child4>}, so {@code payload}'s
   * declared type erases like {@code Object} and {@link
   * com.agentengine.util.common.ObjectTypingModule} alone recovers it; the nested {@code
   * InnerParent<String>} value survives via its own class-level {@code @JsonTypeInfo}, matching the
   * "class-level always wins" convention.
   */
  @Test
  void sequencedEventPayloadSurvivesViaErasureAndNestedSelfDescribingType() throws Exception {
    final ObjectMapper mapper = factory().newObjectMapper("test", new JsonFactory());

    final SequencedEvent<Child4> event =
        new SequencedEvent<>(1L, new Child4(new InnerChild1<>("x")));
    final String json = mapper.writeValueAsString(event);

    final SequencedEvent<?> roundTripped = mapper.readValue(json, SequencedEvent.class);

    assertThat(roundTripped.sequence()).isEqualTo(1L);
    assertThat(roundTripped.payload()).isEqualTo(new Child4(new InnerChild1<>("x")));
  }

  /**
   * The point of {@link PekkoJsonCodec#buildMapper(JsonFactory)}: Pekko's actual jackson-cbor
   * binding passes a {@link CBORFactory}, not the JSON factory the other tests above use for
   * readability — this proves every module/config configured above survives that swap, not just
   * JSON text.
   */
  @Test
  void copyMapperPreservesConfigurationWhenSwappedToCbor() throws Exception {
    final ObjectMapper mapper = factory().newObjectMapper("test", new CBORFactory());

    final Map<String, Object> original = Map.of("original", new Child3(new Child1("nested")));
    final byte[] cbor =
        mapper.writerFor(mapper.constructType(Map.class)).writeValueAsBytes(original);

    @SuppressWarnings("unchecked")
    final Map<String, Object> roundTripped = mapper.readValue(cbor, Map.class);

    assertThat(roundTripped.get("original")).isEqualTo(new Child3(new Child1("nested")));
  }

  /**
   * An {@code Object}-declared map value that is itself a heterogeneous {@code List}, not a bean.
   */
  @Test
  void objectDeclaredMapValueThatIsItselfAListPreservesEachElement() throws Exception {
    final ObjectMapper mapper = factory().newObjectMapper("test", new JsonFactory());

    final List<Parent<?>> innerList =
        List.of(new Child1("a"), new Child2(1), new Child3(new Child1("nested")));
    final Map<String, Object> original = Map.of("items", innerList);
    final String json =
        mapper.writerFor(mapper.constructType(Map.class)).writeValueAsString(original);

    final Map<String, Object> roundTripped = mapper.readValue(json, Map.class);

    assertThat(roundTripped.get("items")).isEqualTo(innerList);
  }

  /** An {@code Object}-declared map value containing a null-element list, and an empty list. */
  @Test
  void objectDeclaredMapValuesHandleNullElementsAndEmptyCollections() throws Exception {
    final ObjectMapper mapper = factory().newObjectMapper("test", new JsonFactory());

    final List<Parent<?>> withNull = new ArrayList<>();
    withNull.add(new Child1("a"));
    withNull.add(null);
    final Map<String, Object> original = Map.of("withNull", withNull, "empty", List.of());
    final String json =
        mapper.writerFor(mapper.constructType(Map.class)).writeValueAsString(original);

    final Map<String, Object> roundTripped = mapper.readValue(json, Map.class);

    assertThat(roundTripped.get("withNull")).isEqualTo(withNull);
    assertThat((List<?>) roundTripped.get("empty")).isEmpty();
  }

  /**
   * The same four-level unannotated generic chain exercised in the gRPC layer's {@code
   * GRPCServerImplTest}, but nested inside an {@code Object}-declared map value instead of a
   * declared {@code Parent<?>} slot — proves {@code JAVA_LANG_OBJECT}, not just {@code NON_FINAL},
   * resolves multi-level generic inheritance.
   */
  @Test
  void objectDeclaredMapValuePreservesDeepGenericChain() throws Exception {
    final ObjectMapper mapper = factory().newObjectMapper("test", new JsonFactory());

    final ConcreteGreatGrandchild original =
        new ConcreteGreatGrandchild(new Child3(new Child1("leaf")), "tag", "extra");
    final Map<String, Object> map = Map.of("chain", original);
    final String json = mapper.writerFor(mapper.constructType(Map.class)).writeValueAsString(map);

    final Map<String, Object> roundTripped = mapper.readValue(json, Map.class);

    assertThat(roundTripped.get("chain"))
        .isInstanceOf(ConcreteGreatGrandchild.class)
        .isEqualTo(original);
  }
}
