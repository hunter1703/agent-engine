package com.agentengine.util.scripts;

import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.scripts.templated.GroovyTemplateProcessor;
import com.agentengine.util.scripts.templated.StringTemplate;
import com.agentengine.util.scripts.templated.Template;
import com.agentengine.util.scripts.templated.TemplatedList;
import com.agentengine.util.scripts.templated.TemplatedMap;
import com.agentengine.util.scripts.templated.TemplatedObject;
import com.hubspot.jinjava.Jinjava;
import java.util.List;
import java.util.Map;
import org.codehaus.groovy.control.CompilerConfiguration;

public final class TemplateUtils {
  private static final Jinjava JINJAVA = new Jinjava();
  private static final GroovyTemplateProcessor GROOVY_TEMPLATE_PROCESSOR =
      new GroovyTemplateProcessor(new CompilerConfiguration());

  private TemplateUtils() {}

  public static String renderTextTemplate(
      final String template, final Map<String, Object> context) {
    if (template == null) {
      return "";
    }
    return JINJAVA.render(template, context).trim();
  }

  public static String renderTemplateForName(final String name, final Map<String, Object> context) {
    return renderTextTemplate(ResourceUtils.loadResourceAsString("/prompts/" + name), context);
  }

  /**
   * Builds a Groovy-backed {@link Template} from a raw string, map, or list, recursing into nested
   * maps/lists so an entire config object graph (e.g. a connector spec) can be templatized
   * field-by-field and evaluated against a runtime params map. Strings containing no {@code {{ }}}
   * expression are wrapped as a constant, skipping Groovy compilation entirely.
   */
  @SuppressWarnings("unchecked")
  public static <T> Template<T> buildTemplate(final Object value) {
    if (value == null) {
      return null;
    }
    return (Template<T>)
        switch (value) {
          case String str -> new TemplatedObject<>(str).build();
          case Map<?, ?> map -> new TemplatedMap<>(map).build();
          case List<?> list -> new TemplatedList<>(list).build();
          default -> Template.constant(value);
        };
  }

  static boolean isTemplateString(final String str) {
    if (StringUtils.isBlank(str)) {
      return false;
    }
    final int index = str.indexOf("{{");
    return index != -1 && str.indexOf("}}", index) != -1;
  }

  public static <T> Template<T> buildStringTemplate(final String template) {
    if (!isTemplateString(template)) {
      //noinspection unchecked
      return (Template<T>) new StringTemplate(template);
    }
    return GROOVY_TEMPLATE_PROCESSOR.build(template);
  }
}
