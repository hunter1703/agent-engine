package com.localagent.engine.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.localagent.engine.config.AgentConfig;
import org.yaml.snakeyaml.Yaml;

public final class YamlUtils {
    private static final Yaml YAML = new Yaml();

    private YamlUtils() {}

    public static Map<String, Object> toMap(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return null;
        }
        return YAML.load(yaml);
    }

    public static <T> T fromFile(Path path, Class<T> clazz) {
        try {
            return YAML.loadAs(Files.newInputStream(path), clazz);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
