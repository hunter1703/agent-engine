package com.agentengine.interfaces.rest.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.util.agents.beans.config.ChatModelConfig;
import com.agentengine.util.agents.beans.config.EmbeddingModelConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.testfixtures.Child1;
import com.agentengine.util.common.testfixtures.Parent;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * REST layer coverage: {@link RestJsonCodec} deliberately has no default typing (see its javadoc).
 * The negative test below is the important one — it proves that boundary is real, not just
 * documented.
 */
class RestJsonCodecTest {

  private static RestJsonCodec codec() {
    return new RestJsonCodec(List.of());
  }

  /** {@link ModelConfig}'s own closed {@code @JsonTypeInfo(Id.NAME)} needs no default typing. */
  @Test
  void closedSubtypeRoundTripsThroughItsOwnAnnotation() {
    final RestJsonCodec codec = codec();
    final EmbeddingModelConfig original = new EmbeddingModelConfig();
    original.setName("embedder");
    original.setDimensions(768);

    final String json = codec.serialize(original);
    final ModelConfig roundTripped = codec.deserialize(json, ModelConfig.class);

    assertThat(roundTripped).isInstanceOf(EmbeddingModelConfig.class);
    assertThat(roundTripped.getName()).isEqualTo("embedder");
    assertThat(((EmbeddingModelConfig) roundTripped).getDimensions()).isEqualTo(768);
  }

  /**
   * The actual security boundary: an unannotated polymorphic hierarchy (no {@code @JsonTypeInfo} of
   * its own) can't be recovered through this codec, because there's no default typing to fall back
   * on. If this ever starts passing, {@link RestJsonCodec}'s "no default typing" guarantee broke.
   */
  @Test
  void unannotatedHierarchyCannotBeRecoveredWithoutDefaultTyping() {
    final RestJsonCodec codec = codec();
    final String json = codec.serialize(new Child1("value"));

    assertThat(json).doesNotContain("@class");
    assertThatThrownBy(() -> codec.deserialize(json, Parent.class))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void nonAbsentInclusionOmitsNullFields() {
    final RestJsonCodec codec = codec();

    final String json = codec.serialize(new EmbeddingModelConfig());

    assertThat(json).doesNotContain("baseUrl").doesNotContain("apiKey");
  }

  // Exists only so its generic signature can be captured as a real List<ModelConfig> Type below.
  private static List<ModelConfig> listOfModelConfigSignature() {
    return null;
  }

  /**
   * A heterogeneous {@code List<ModelConfig>} (the shape {@code ModelService.findModels} actually
   * returns, wrapped in {@code PaginatedResult}) — proves the closed-subtype mechanism resolves
   * each element correctly inside a collection, not just a single top-level value.
   */
  @Test
  void heterogeneousListOfClosedSubtypesRoundTrips() throws Exception {
    final RestJsonCodec codec = codec();
    final ChatModelConfig chat = new ChatModelConfig();
    chat.setName("chat-model");
    final EmbeddingModelConfig embedding = new EmbeddingModelConfig();
    embedding.setName("embedding-model");
    embedding.setDimensions(1536);

    final Type listType =
        RestJsonCodecTest.class
            .getDeclaredMethod("listOfModelConfigSignature")
            .getGenericReturnType();
    final String json = codec.serialize(List.of(chat, embedding), listType);
    @SuppressWarnings("unchecked")
    final List<ModelConfig> roundTripped = (List<ModelConfig>) codec.deserialize(json, listType);

    assertThat(roundTripped).hasSize(2);
    assertThat(roundTripped.get(0)).isInstanceOf(ChatModelConfig.class);
    assertThat(roundTripped.get(0).getName()).isEqualTo("chat-model");
    assertThat(roundTripped.get(1)).isInstanceOf(EmbeddingModelConfig.class);
    assertThat(((EmbeddingModelConfig) roundTripped.get(1)).getDimensions()).isEqualTo(1536);
  }
}
