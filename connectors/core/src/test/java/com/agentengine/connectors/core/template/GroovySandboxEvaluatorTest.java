package com.agentengine.connectors.core.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroovySandboxEvaluatorTest {

  private final GroovySandboxEvaluator evaluator = new GroovySandboxEvaluator();

  @Test
  void evaluatesSimpleExpressions() {
    assertThat(evaluator.evaluate("a + b", Map.of("a", 10, "b", 5))).isEqualTo(15);
    assertThat(evaluator.evaluate("text.toUpperCase()", Map.of("text", "hello")))
        .isEqualTo("HELLO");
    assertThat(evaluator.evaluate("user.id", Map.of("user", Map.of("id", 42)))).isEqualTo(42);
  }

  @Test
  void rejectsEmptyAndMultilineExpressions() {
    assertThatThrownBy(() -> evaluator.evaluate("", Map.of()))
        .isInstanceOf(TemplateResolutionException.class)
        .hasMessageContaining("Expression cannot be empty");
    assertThatThrownBy(() -> evaluator.evaluate("1 + 1;\n2 + 2", Map.of()))
        .isInstanceOf(TemplateResolutionException.class)
        .hasMessageContaining("single-line");
  }

  @Test
  void rejectsExpressionsLongerThanLimit() {
    final GroovySandboxEvaluator shortEvaluator =
        new GroovySandboxEvaluator(Duration.ofMillis(500), 5);
    assertThatThrownBy(() -> shortEvaluator.evaluate("123456", Map.of()))
        .isInstanceOf(TemplateResolutionException.class)
        .hasMessageContaining("max length");
  }

  @Test
  void wrapsMissingVariableErrorsAsUnresolvedVariableException() {
    assertThatThrownBy(() -> evaluator.evaluate("a + b", Map.of("a", 10)))
        .isInstanceOf(UnresolvedVariableException.class)
        .hasMessageContaining("missing variable");
  }

  @Test
  void blocksDangerousExpressions() {
    assertThatThrownBy(() -> evaluator.evaluate("System.exit(0)", Map.of()))
        .isInstanceOf(TemplateResolutionException.class)
        .hasMessageContaining("blocked token");
    assertThatThrownBy(() -> evaluator.evaluate("Runtime.getRuntime().exec('ls')", Map.of()))
        .isInstanceOf(TemplateResolutionException.class)
        .hasMessageContaining("blocked token");
    assertThatThrownBy(() -> evaluator.evaluate("''.getClass().getClassLoader()", Map.of()))
        .isInstanceOf(TemplateResolutionException.class)
        .hasMessageContaining("blocked token");
  }
}
