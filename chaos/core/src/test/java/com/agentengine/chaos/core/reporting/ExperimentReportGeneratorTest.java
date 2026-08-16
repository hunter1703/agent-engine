package com.agentengine.chaos.core.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.chaos.api.EvaluationResult;
import com.agentengine.chaos.api.ExperimentResult;
import com.agentengine.chaos.api.ExperimentStatus;
import com.agentengine.chaos.api.ReportFormat;
import com.agentengine.chaos.api.SteadyStateMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExperimentReportGeneratorTest {

  private final ExperimentReportGenerator generator = new ExperimentReportGenerator();

  @Test
  void shouldGenerateJsonReportContainingKeyFields() {
    final String json = generator.generate(sampleResult(), ReportFormat.JSON);

    assertThat(json)
        .contains("\"experimentId\"")
        .contains("runtime-pod-kill-recovery")
        .contains("PASSED");
  }

  @Test
  void shouldGenerateMarkdownReportContainingKeyFields() {
    final String markdown = generator.generate(sampleResult(), ReportFormat.MARKDOWN);

    assertThat(markdown)
        .contains("# Chaos Experiment Report: runtime-pod-kill-recovery")
        .contains("Steady State")
        .contains("Success Criteria");
  }

  @Test
  void shouldGenerateHtmlReportContainingKeyFields() {
    final String html = generator.generate(sampleResult(), ReportFormat.HTML);

    assertThat(html)
        .contains("<html>")
        .contains("runtime-pod-kill-recovery")
        .contains("Steady State");
  }

  @Test
  void shouldRejectUnknownFormat() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> generator.generate(sampleResult(), ReportFormat.UNKNOWN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static ExperimentResult sampleResult() {
    final SteadyStateMetrics metrics =
        new SteadyStateMetrics(
            0.99,
            Duration.ofMillis(50),
            Duration.ofMillis(90),
            Duration.ofMillis(120),
            0.01,
            3,
            Duration.ZERO,
            Duration.ZERO,
            0,
            Instant.parse("2026-01-01T00:00:00Z"));

    return new ExperimentResult(
        "exp-123",
        "runtime-pod-kill-recovery",
        Instant.parse("2026-01-01T00:00:00Z"),
        Optional.of(Instant.parse("2026-01-01T00:02:00Z")),
        ExperimentStatus.PASSED,
        Optional.of(metrics),
        List.of(metrics),
        Optional.of(metrics),
        Optional.of(new EvaluationResult(true, List.of(), metrics, List.of(metrics), metrics)),
        List.of(),
        Optional.of(Duration.ofSeconds(20)),
        Optional.empty());
  }
}
