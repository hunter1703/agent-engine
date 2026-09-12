package com.agentengine.util.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.query.PaginatedResult;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@link FunctionUtils#buildRawStub} is the mechanism the raw-response-passthrough optimization
 * relies on: a caller that never touches the object it got back from a microservice call should
 * never pay Jackson's deserialize cost, while a caller that does touch it should see exactly the
 * same data (and the same exception behavior) as if it had been deserialized normally. These tests
 * verify both halves of that contract directly against the generated stub, without needing a live
 * gRPC call.
 */
class RawBytesFunctionsTest {

  private static Type paginatedResultOf(final Type elementType) {
    return new ParameterizedType() {
      @Override
      public Type[] getActualTypeArguments() {
        return new Type[] {elementType};
      }

      @Override
      public Type getRawType() {
        return PaginatedResult.class;
      }

      @Override
      public Type getOwnerType() {
        return null;
      }
    };
  }

  @Test
  void rawBytesAccessorReturnsOriginalBytesWithoutDeserializing() {
    final AtomicInteger deserializeCalls = new AtomicInteger();
    final byte[] raw =
        "{\"items\":[\"a\",\"b\"],\"hasMore\":false}".getBytes(StandardCharsets.UTF_8);
    final JsonCodec codec = countingCodec(deserializeCalls);

    final PaginatedResult<?> stub =
        FunctionUtils.buildRawStub(
            paginatedResultOf(String.class), PaginatedResult.class, codec, raw);

    assertThat(stub).isInstanceOf(RawBytes.class);
    assertThat(((RawBytes) stub).bytes()).isEqualTo(raw);
    assertThat(deserializeCalls).hasValue(0);
  }

  @Test
  void touchingAnyMethodMaterializesRealDataExactlyOnce() {
    final AtomicInteger deserializeCalls = new AtomicInteger();
    final byte[] raw =
        "{\"items\":[\"a\",\"b\"],\"hasMore\":true}".getBytes(StandardCharsets.UTF_8);
    final JsonCodec codec = countingCodec(deserializeCalls);

    @SuppressWarnings("unchecked")
    final PaginatedResult<String> stub =
        (PaginatedResult<String>)
            FunctionUtils.buildRawStub(
                paginatedResultOf(String.class), PaginatedResult.class, codec, raw);

    assertThat(stub.getItems()).containsExactly("a", "b");
    assertThat(stub.isHasMore()).isTrue();
    assertThat(stub.getItems()).containsExactly("a", "b");
    // Every method call above should have shared the same one-time deserialize, not repeated it.
    assertThat(deserializeCalls).hasValue(1);
  }

  @Test
  void matchesWhatNormalDeserializationWouldHaveProduced() {
    final DefaultJsonCodec codec = new DefaultJsonCodec(List.of());
    final byte[] raw =
        "{\"items\":[\"a\",\"b\"],\"hasMore\":true}".getBytes(StandardCharsets.UTF_8);
    final Type type = paginatedResultOf(String.class);

    @SuppressWarnings("unchecked")
    final PaginatedResult<String> stub =
        (PaginatedResult<String>)
            FunctionUtils.buildRawStub(type, PaginatedResult.class, codec, raw);
    final PaginatedResult<?> normal = codec.deserialize(new ByteArrayInputStream(raw), type);

    assertThat(stub.getItems()).isEqualTo(normal.getItems());
    assertThat(stub.isHasMore()).isEqualTo(normal.isHasMore());
  }

  @Test
  void transformMaterializesAndReturnsARealNonStubResult() {
    final byte[] raw =
        "{\"items\":[\"a\",\"bb\"],\"hasMore\":false}".getBytes(StandardCharsets.UTF_8);
    final DefaultJsonCodec codec = new DefaultJsonCodec(List.of());

    @SuppressWarnings("unchecked")
    final PaginatedResult<String> stub =
        (PaginatedResult<String>)
            FunctionUtils.buildRawStub(
                paginatedResultOf(String.class), PaginatedResult.class, codec, raw);

    final PaginatedResult<Integer> lengths = stub.transform(String::length);

    assertThat(lengths.getItems()).containsExactly(1, 2);
    // transform() builds a genuinely new PaginatedResult -- passthrough correctly stops here rather
    // than smuggling raw bytes past a caller that actually reshaped the data.
    assertThat(lengths).isNotInstanceOf(RawBytes.class);
  }

  // Mirrors BaseAgentConfig's constructor shape (non-public no-arg constructor) without also
  // pulling in Jackson's polymorphic-abstract-type handling, which is a separate, already-covered
  // concern -- the default ByteBuddy constructor strategy only mirrors *public* super constructors,
  // which would otherwise silently leave the generated subclass uninstantiable for this shape.
  static class ProtectedCtorDto {
    private String label;

    protected ProtectedCtorDto() {}

    public String getLabel() {
      return label;
    }

    public void setLabel(final String label) {
      this.label = label;
    }
  }

  @Test
  void supportsClassesWithNonPublicNoArgConstructors() {
    assertThat(FunctionUtils.canBuildRawStub(ProtectedCtorDto.class)).isTrue();

    final byte[] raw = "{\"label\":\"hi\"}".getBytes(StandardCharsets.UTF_8);
    final ProtectedCtorDto stub =
        FunctionUtils.buildRawStub(
            ProtectedCtorDto.class, ProtectedCtorDto.class, new DefaultJsonCodec(List.of()), raw);

    assertThat(stub).isInstanceOf(RawBytes.class);
    assertThat(stub.getLabel()).isEqualTo("hi");
  }

  @Test
  void supportsInterfaceReturnTypesLikeMap() {
    assertThat(FunctionUtils.canBuildRawStub(java.util.Map.class)).isTrue();

    final byte[] raw = "{\"a\":\"1\",\"b\":\"2\"}".getBytes(StandardCharsets.UTF_8);
    final Type mapOfStringString =
        new ParameterizedType() {
          @Override
          public Type[] getActualTypeArguments() {
            return new Type[] {String.class, String.class};
          }

          @Override
          public Type getRawType() {
            return java.util.Map.class;
          }

          @Override
          public Type getOwnerType() {
            return null;
          }
        };

    @SuppressWarnings("unchecked")
    final java.util.Map<String, String> stub =
        (java.util.Map<String, String>)
            FunctionUtils.buildRawStub(
                mapOfStringString, java.util.Map.class, new DefaultJsonCodec(List.of()), raw);

    assertThat(stub).isInstanceOf(RawBytes.class);
    assertThat(stub).containsEntry("a", "1").containsEntry("b", "2");
  }

  @Test
  void isSupportedRejectsPrimitivesAndFinalClasses() {
    assertThat(FunctionUtils.canBuildRawStub(boolean.class)).isFalse();
    assertThat(FunctionUtils.canBuildRawStub(String.class)).isFalse(); // final
    assertThat(FunctionUtils.canBuildRawStub(java.util.Optional.class)).isFalse(); // final
  }

  private static JsonCodec countingCodec(final AtomicInteger counter) {
    return new DefaultJsonCodec(List.of()) {
      @Override
      public <T> T deserialize(final InputStream inputStream, final Type type) {
        counter.incrementAndGet();
        return super.deserialize(inputStream, type);
      }
    };
  }
}
