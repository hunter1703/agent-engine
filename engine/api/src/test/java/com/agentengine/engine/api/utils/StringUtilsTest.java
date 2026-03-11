package com.agentengine.engine.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.StringUtils;
import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  void shouldTreatWhitespaceAsBlankWhenCheckingBlank() {
    assertThat(StringUtils.isBlank("  ")).isTrue();
    assertThat(StringUtils.isNotBlank("value")).isTrue();
  }

  @Test
  void shouldReturnNullWhenSubstringCalledWithNullInput() {
    assertThat(StringUtils.substring(null, 0, 2)).isNull();
  }

  @Test
  void shouldReturnExpectedSubstringWhenUsingNegativeIndexes() {
    assertThat(StringUtils.substring("abcdef", -4, -1)).isEqualTo("cde");
  }

  @Test
  void shouldReturnEmptyStringWhenStartAfterEnd() {
    assertThat(StringUtils.substring("abcdef", 5, 2)).isEmpty();
  }
}
