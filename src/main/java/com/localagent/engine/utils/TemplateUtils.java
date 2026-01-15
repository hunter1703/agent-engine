package com.localagent.engine.utils;

import com.hubspot.jinjava.Jinjava;

import java.util.HashMap;
import java.util.Map;

public final class TemplateUtils {
    private static final Jinjava JINJAVA = new Jinjava();

    private TemplateUtils() {}

    public static String render(String template, Map<String, Object> context) {
        if (template == null) {
            return "";
        }
        return JINJAVA.render(template, context).trim();
    }

    public static String renderForName(String name, Map<String, Object> context) {
        return render(ResourceUtils.loadResourceAsString("/prompts/" + name), context);
    }

    public static String renderProtocol(String template, String thoughtTag, boolean thoughtsEnabled) {
        Map<String, Object> context = new HashMap<>();
        context.put("thought_tag", thoughtTag == null ? "think" : thoughtTag);
        context.put("thoughts_enabled", thoughtsEnabled);
        return TemplateUtils.render(template, context);
    }

    public static String renderRouter(String template, Map<String, String> values) {
        Map<String, Object> context = new HashMap<>(values);
        return TemplateUtils.render(template, context);
    }
}
