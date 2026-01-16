package com.localagent.engine.utils;

import com.hubspot.jinjava.Jinjava;

import java.util.HashMap;
import java.util.Map;

public final class TemplateUtils {
    private static final Jinjava JINJAVA = new Jinjava();

    private TemplateUtils() {}

    public static String render(final String template, final Map<String, Object> context) {
        if (template == null) {
            return "";
        }
        return JINJAVA.render(template, context).trim();
    }

    public static String renderForName(final String name, final Map<String, Object> context) {
        return render(ResourceUtils.loadResourceAsString("/prompts/" + name), context);
    }

    public static String renderProtocol(final String template, final String thoughtTag, final boolean thoughtsEnabled) {
        final Map<String, Object> context = new HashMap<>();
        context.put("thought_tag", thoughtTag == null ? "think" : thoughtTag);
        context.put("thoughts_enabled", thoughtsEnabled);
        return TemplateUtils.render(template, context);
    }

    public static String renderRouter(final String template, final Map<String, String> values) {
        final Map<String, Object> context = new HashMap<>(values);
        return TemplateUtils.render(template, context);
    }
}
