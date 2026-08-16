package com.agentengine.chaos.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnumValueOfOrDefaultTest {

  @Test
  void faultTypeShouldParseKnownAndUnknownValues() {
    assertThat(FaultType.valueOfOrDefault("POD_KILL")).isEqualTo(FaultType.POD_KILL);
    assertThat(FaultType.valueOfOrDefault("qdrant_failure")).isEqualTo(FaultType.QDRANT_FAILURE);
    assertThat(FaultType.valueOfOrDefault("not-a-real-fault")).isEqualTo(FaultType.UNKNOWN);
    assertThat(FaultType.valueOfOrDefault(null)).isEqualTo(FaultType.UNKNOWN);
    assertThat(FaultType.valueOfOrDefault("")).isEqualTo(FaultType.UNKNOWN);
  }

  @Test
  void blastRadiusScopeShouldParseKnownAndUnknownValues() {
    assertThat(BlastRadiusScope.valueOfOrDefault("SERVICE")).isEqualTo(BlastRadiusScope.SERVICE);
    assertThat(BlastRadiusScope.valueOfOrDefault("garbage")).isEqualTo(BlastRadiusScope.UNKNOWN);
  }

  @Test
  void experimentStatusShouldParseKnownAndUnknownValues() {
    assertThat(ExperimentStatus.valueOfOrDefault("PASSED")).isEqualTo(ExperimentStatus.PASSED);
    assertThat(ExperimentStatus.valueOfOrDefault("garbage")).isEqualTo(ExperimentStatus.UNKNOWN);
  }

  @Test
  void criterionTypeShouldParseKnownAndUnknownValues() {
    assertThat(CriterionType.valueOfOrDefault("ZERO_DATA_LOSS"))
        .isEqualTo(CriterionType.ZERO_DATA_LOSS);
    assertThat(CriterionType.valueOfOrDefault("garbage")).isEqualTo(CriterionType.UNKNOWN);
  }

  @Test
  void faultOutcomeShouldParseKnownAndUnknownValues() {
    assertThat(FaultOutcome.valueOfOrDefault("INJECTED")).isEqualTo(FaultOutcome.INJECTED);
    assertThat(FaultOutcome.valueOfOrDefault("garbage")).isEqualTo(FaultOutcome.UNKNOWN);
  }
}
