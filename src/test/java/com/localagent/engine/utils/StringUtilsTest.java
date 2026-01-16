package com.localagent.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StringUtilsTest {

  @Test
  void isBlankDetectsNullAndWhitespace() {
    assertThat(StringUtils.isBlank(null)).isTrue();
    assertThat(StringUtils.isBlank("   ")).isTrue();
    assertThat(StringUtils.isBlank("text")).isFalse();
  }

  @Test
  void isNotBlankIsInverse() {
    assertThat(StringUtils.isNotBlank("text")).isTrue();
    assertThat(StringUtils.isNotBlank(" ")).isFalse();
  }
}
