package com.agentengine.connectors.core.template;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import jakarta.inject.Singleton;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

@Singleton
public final class GroovySandboxEvaluator {

  private static final List<String> BLOCKED_SUBSTRINGS = List.of("classloader", "metaclass", "class.forname", "runtime.", "system.",
      "thread.", "processbuilder", "new file", "new url", "import ", "@grab");
  private static final ExecutorService EVALUATION_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  static {
    Runtime.getRuntime().addShutdownHook(new Thread(EVALUATION_EXECUTOR::shutdownNow));
  }

  private final Duration timeout;
  private final int maxExpressionLength;
  private final CompilerConfiguration compilerConfiguration;

  public GroovySandboxEvaluator() {
    this(Duration.ofMillis(10000), 2_000);
  }

  public GroovySandboxEvaluator(final Duration timeout, final int maxExpressionLength) {
    this.timeout = timeout;
    this.maxExpressionLength = maxExpressionLength;
    this.compilerConfiguration = createCompilerConfiguration();
  }

  public Object evaluate(final String expression, final Map<String, Object> variables) {
    if (expression == null || expression.isBlank()) {
      throw new TemplateResolutionException("Expression cannot be empty");
    }

    final String trimmed = expression.trim();
    if (trimmed.length() > maxExpressionLength) {
      throw new TemplateResolutionException("Expression exceeds max length of " + maxExpressionLength + " characters");
    }

    if (trimmed.contains(";") || trimmed.contains("\n")) {
      throw new TemplateResolutionException("Only single-line expressions are allowed");
    }

    final String normalized = trimmed.toLowerCase();
    for (String blockedSubstring : BLOCKED_SUBSTRINGS) {
      if (normalized.contains(blockedSubstring)) {
        throw new TemplateResolutionException("Expression contains blocked token: " + blockedSubstring);
      }
    }

    final Future<Object> task = EVALUATION_EXECUTOR.submit(() -> {
      final Binding binding = new Binding(wrapVariables(variables));
      final GroovyShell shell = new GroovyShell(binding, compilerConfiguration);
      return shell.evaluate(trimmed);
    });
    try {
      return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      task.cancel(true);
      throw new TemplateResolutionException("Expression evaluation timed out", ex);
    } catch (ExecutionException ex) {
      final Throwable cause = ex.getCause();
      if (cause instanceof MissingPropertyException || cause instanceof MissingMethodException) {
        throw new UnresolvedVariableException("Expression references missing variable", cause);
      }
      if (cause instanceof MultipleCompilationErrorsException) {
        throw new TemplateResolutionException("Expression failed sandbox validation", cause);
      }
      throw new TemplateResolutionException("Expression evaluation failed", cause);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new TemplateResolutionException("Expression evaluation interrupted", ex);
    }
  }

  private static CompilerConfiguration createCompilerConfiguration() {
    final CompilerConfiguration configuration = new CompilerConfiguration();

    final SecureASTCustomizer secureASTCustomizer = new SecureASTCustomizer();
    secureASTCustomizer.setClosuresAllowed(false);
    secureASTCustomizer.setMethodDefinitionAllowed(false);
    secureASTCustomizer.setPackageAllowed(false);
    secureASTCustomizer.setIndirectImportCheckEnabled(false);
    secureASTCustomizer.setImportsWhitelist(List.of());
    secureASTCustomizer.setStarImportsWhitelist(List.of());
    secureASTCustomizer.setStaticImportsWhitelist(List.of());
    secureASTCustomizer.setStaticStarImportsWhitelist(List.of());
    secureASTCustomizer.setConstantTypesClassesWhiteList(List.of(String.class, Integer.class, Long.class, Double.class, Float.class,
        BigDecimal.class, BigInteger.class, Boolean.class, Map.class, List.class, Object.class));
    secureASTCustomizer.setReceiversClassesWhiteList(List.of(Object.class, String.class, Number.class, Integer.class, Long.class,
        Double.class, Float.class, BigDecimal.class, BigInteger.class, Boolean.class, Map.class, List.class));

    configuration.addCompilationCustomizers(secureASTCustomizer);
    return configuration;
  }

  private static Map<String, Object> wrapVariables(final Map<String, Object> variables) {
    final Map<String, Object> wrapped = new LinkedHashMap<>();
    variables.forEach((key, value) -> wrapped.put(key, wrapValue(value)));
    return new StrictMap(wrapped);
  }

  @SuppressWarnings("unchecked")
  private static Object wrapValue(final Object value) {
    if (value instanceof Map<?, ?> mapValue) {
      final Map<String, Object> wrapped = new LinkedHashMap<>();
      mapValue.forEach((key, entryValue) -> wrapped.put(String.valueOf(key), wrapValue(entryValue)));
      return new StrictMap(wrapped);
    }
    if (value instanceof List<?> listValue) {
      final List<Object> wrapped = new ArrayList<>(listValue.size());
      listValue.forEach(item -> wrapped.add(wrapValue(item)));
      return wrapped;
    }
    return value;
  }

  private static final class StrictMap extends LinkedHashMap<String, Object> {
    private StrictMap(final Map<String, Object> values) {
      super(values);
    }

    @Override
    public Object get(final Object key) {
      if (!containsKey(key)) {
        throw new MissingPropertyException(String.valueOf(key), StrictMap.class);
      }
      return super.get(key);
    }
  }
}
