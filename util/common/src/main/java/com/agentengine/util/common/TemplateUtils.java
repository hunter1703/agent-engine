package com.agentengine.util.common;

import com.hubspot.jinjava.Jinjava;
import java.util.Map;

public final class TemplateUtils {
    private static final Jinjava JINJAVA = new Jinjava();

    private TemplateUtils() {}

    public static String renderTextTemplate(final String template, final Map<String, Object> context) {
        if (template == null) {
            return "";
        }
        return JINJAVA.render(template, context).trim();
    }

    public static String renderTemplateForName(final String name, final Map<String, Object> context) {
        return renderTextTemplate(ResourceUtils.loadResourceAsString("/prompts/" + name), context);
    }
}
