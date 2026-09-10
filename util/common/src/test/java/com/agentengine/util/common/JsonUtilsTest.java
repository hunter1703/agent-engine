package com.agentengine.util.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonUtilsTest {

  private record Sample(String name, long value) {}

  @Test
  void toJsonThenFromJsonRoundTrips() {
    final Sample sample = new Sample("alpha", 42L);

    final String json = JsonUtils.toJson(sample);
    final Sample roundTripped = JsonUtils.fromJson(json, Sample.class);

    assertThat(roundTripped).isEqualTo(sample);
  }

  @Test
  void toMapThenFromMapRoundTrips() {
    final Sample sample = new Sample("beta", 7L);

    final Map<String, Object> map = JsonUtils.toMap(sample);
    final Sample roundTripped = JsonUtils.fromMap(map, Sample.class);

    assertThat(map).containsEntry("name", "beta").containsEntry("value", 7L);
    assertThat(roundTripped).isEqualTo(sample);
  }

  @Test
  void toJsonOnNullReturnsNull() {
    assertThat(JsonUtils.toJson(null)).isNull();
  }

  @Test
  void fromJsonOnBlankReturnsNull() {
    assertThat(JsonUtils.<Sample>fromJson("", Sample.class)).isNull();
    assertThat(JsonUtils.<Sample>fromJson((String) null, Sample.class)).isNull();
  }
}
