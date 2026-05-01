package com.agentengine.util.common;

import com.agentengine.util.common.beans.FileDetails;

import java.util.Set;

public final class FileUtils {

    private static final Set<String> TEXT_MIME_TYPES =
            Set.of("application/json", "application/xml", "application/yaml", "application/x-yaml");
    private static final Set<String> TEXT_EXTENSIONS =
            Set.of(".txt", ".md", ".json", ".yaml", ".yml", ".csv", ".xml", ".log", ".properties");

    private FileUtils() {}

    public static boolean isTextFile(final FileDetails fileDetails) {
        final String mimeType = fileDetails.mimeType();
        if (mimeType != null) {
            final String lower = mimeType.toLowerCase();
            if (lower.startsWith("text/") || TEXT_MIME_TYPES.contains(lower)) {
                return true;
            }
        }
        final String name = fileDetails.name();
        if (name != null) {
            final int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                return TEXT_EXTENSIONS.contains(name.substring(dot).toLowerCase());
            }
        }
        return false;
    }
}
