package com.agentengine.util.common.beans;

import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.common.service.CloudStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Generic file reference that is storage-backend agnostic.
 *
 * <p>For {@link StorageType#CLOUDSTORAGE}, {@code path} is {@code <bucket>/<key>}.
 *
 * @param name         original filename including extension (e.g. {@code photo.jpg})
 * @param path         storage-specific path to the file
 * @param type         storage backend type
 * @param mimeType     optional MIME type (e.g. {@code "image/jpeg"})
 * @param size         optional file size in bytes; {@code -1} if unknown
 */
public record FileDetails(
        @ToolSchema(name = "name", description = "Original filename including extension, e.g. photo.jpg. Used to determine file format.")
        String name,

        @ToolSchema(name = "path", description = "Storage path to the file. For CLOUDSTORAGE, format is <bucket>/<key>.")
        String path,

        @ToolSchema(name = "type", description = "Storage backend type.", enums = {"CLOUDSTORAGE", "UNKNOWN"})
        StorageType type,

        @ToolSchema(name = "mimeType", description = "MIME type of the file, e.g. image/jpeg or image/png.", optional = true)
        String mimeType,

        @ToolSchema(name = "size", description = "File size in bytes. Use -1 if unknown.", optional = true)
        long size,

        @ToolSchema(name = "base64Content", description = "Base64-encoded file content. Leave unset when referencing a stored file; populated automatically when the file is resolved.", optional = true)
        String base64Content) {

    public enum StorageType {
        CLOUDSTORAGE,
        UNKNOWN;

        public static StorageType valueOfOrDefault(final String value) {
            if (value == null) return UNKNOWN;
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }
}
