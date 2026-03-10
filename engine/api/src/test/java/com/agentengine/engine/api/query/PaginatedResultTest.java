package com.agentengine.engine.api.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaginatedResultTest {

  @Test
  void shouldSetNextCursorAndHasMoreWhenMoreDataExistsByTotal() {
    final Page page = new Page(0, 2);

    final PaginatedResult<String> result = PaginatedResult.create(List.of("a", "b"), page, 5L);

    assertThat(result.isHasMore()).isTrue();
    assertThat(result.getNextCursor()).isNotBlank();
    final String decodedCursor =
        new String(Base64.getDecoder().decode(result.getNextCursor()), StandardCharsets.UTF_8);
    assertThat(decodedCursor).contains("\"offset\":2");
    assertThat(decodedCursor).contains("\"limit\":2");
  }

  @Test
  void shouldNotSetNextCursorWhenNoMoreDataExists() {
    final Page page = new Page(0, 10);

    final PaginatedResult<String> result = PaginatedResult.create(List.of("a", "b"), page, 2L);

    assertThat(result.isHasMore()).isFalse();
    assertThat(result.getNextCursor()).isNull();
  }

  @Test
  void shouldTransformItemsWhenTransformCalled() {
    final PaginatedResult<String> source =
        PaginatedResult.create(List.of("1", "2"), new Page(0, 2), 2L);

    final PaginatedResult<Integer> transformed = source.transform(Integer::parseInt);

    assertThat(transformed.getItems()).containsExactly(1, 2);
    assertThat(transformed.getTotal()).isEqualTo(2L);
  }
}
