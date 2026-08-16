package com.agentengine.util.scripts.templated;

import com.agentengine.util.common.StringUtils;
import com.agentengine.util.scripts.exception.TemplateException;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;

public class GroovyTemplateProcessor implements AutoCloseable {

  /**
   * Accepted format: {{ <expression> }}. The match is reluctant so that "{{a}} {{b}}" resolves as
   * two templates instead of one spanning both.
   */
  public static final Pattern TEMPLATE_PATTERN =
      Pattern.compile("\\{\\{(.+?(?=}}))}}", Pattern.DOTALL);

  private final GroovyClassLoader loader;

  public GroovyTemplateProcessor(CompilerConfiguration configuration) {
    configuration.addCompilationCustomizers(secureASTCustomizer());
    this.loader = new GroovyClassLoader(this.getClass().getClassLoader(), configuration);
  }

  /**
   * Expressions are untrusted content (connector spec authors, not the JVM). This whitelist blocks
   * reflection, classloading, and process/file/network escapes while still allowing the plain
   * data-shaping expressions templates are meant for.
   */
  private static SecureASTCustomizer secureASTCustomizer() {
    final SecureASTCustomizer customizer = new SecureASTCustomizer();
    customizer.setClosuresAllowed(false);
    customizer.setMethodDefinitionAllowed(false);
    customizer.setPackageAllowed(false);
    customizer.setIndirectImportCheckEnabled(false);
    customizer.setAllowedImports(List.of());
    customizer.setAllowedStarImports(List.of());
    customizer.setAllowedStaticImports(List.of());
    customizer.setAllowedStaticStarImports(List.of());
    customizer.setAllowedConstantTypesClasses(
        List.of(
            String.class,
            Integer.class,
            Long.class,
            Double.class,
            Float.class,
            BigDecimal.class,
            BigInteger.class,
            Boolean.class,
            Map.class,
            List.class,
            Object.class));
    customizer.setAllowedReceiversClasses(
        List.of(
            Object.class,
            String.class,
            Number.class,
            Integer.class,
            Long.class,
            Double.class,
            Float.class,
            BigDecimal.class,
            BigInteger.class,
            Boolean.class,
            Map.class,
            List.class));
    return customizer;
  }

  @Override
  public void close() throws IOException {
    loader.close();
  }

  public <T> Template<T> build(String template) {
    if (StringUtils.isEmpty(template)) {
      return null;
    }

    final String trimmed = template.trim();
    final List<Template<?>> templates = new ArrayList<>();

    int index = 0;
    final Matcher matcher = TEMPLATE_PATTERN.matcher(trimmed);

    while (matcher.find()) {
      final String expression = matcher.group(1);
      final String literal = trimmed.substring(index, matcher.start());
      if (!StringUtils.isEmpty(literal)) {
        templates.add(new StringTemplate(literal));
      }
      templates.add(compile(expression));
      index = matcher.end();
    }

    if (index < trimmed.length()) {
      templates.add(new StringTemplate(trimmed.substring(index)));
    }

    // A string mixing literal text and expressions, e.g. "Bearer {{ token }}", is
    // concatenated and stringified once every part has been resolved.
    if (templates.size() > 1) {
      //noinspection unchecked
      return (Template<T>) new JoinTemplateImpl(templates);
    }

    //noinspection unchecked
    return (Template<T>) templates.getFirst();
  }

  public <T> T evaluate(String template, Map<String, Object> params) {
    final Template<Object> built = build(template);
    //noinspection unchecked
    return (T) built.getValue(params);
  }

  @SuppressWarnings("unchecked")
  private <T> Template<T> compile(String expression) {
    final Class<? extends Script> scriptClass = loader.parseClass(expression);
    try {
      final Constructor<? extends Script> constructor = scriptClass.getDeclaredConstructor();
      return new GroovyTemplate<>(expression, constructor);
    } catch (NoSuchMethodException e) {
      throw new TemplateException("Failed to build groovy template: " + expression, e);
    }
  }
}
