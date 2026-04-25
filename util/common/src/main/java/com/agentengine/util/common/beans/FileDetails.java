package com.agentengine.util.common.beans;

import com.agentengine.util.common.annotations.ToolSchema;

/**
 * Generic file reference that is storage-backend agnostic.
 *
 * <p>For {@link StorageType#CLOUDSTORAGE}, {@code source} is {@code <bucket>/<key>}.
 *
 * @param name         original filename including extension (e.g. {@code photo.jpg})
 * @param source         storage-specific source to the file
 * @param type         storage backend type
 * @param mimeType     optional MIME type (e.g. {@code "image/jpeg"})
 * @param size         optional file size in bytes; {@code -1} if unknown
 */
public record FileDetails(
        @ToolSchema(name = "name", description = "Original filename including extension, e.g. photo.jpg. Used to determine file format.")
        String name,

        @ToolSchema(name = "source", description = "Storage location of the file. For CLOUDSTORAGE, format is <bucket>/<key>.")
        String source,

        @ToolSchema(name = "type", description = "Storage backend type.", enums = {"CLOUDSTORAGE", "UNKNOWN", "URL"})
        StorageType type,

        @ToolSchema(name = "mimeType", description = "MIME type of the file, e.g. image/jpeg or image/png.", optional = true)
        String mimeType,

        @ToolSchema(name = "size", description = "File size in bytes. Use -1 if unknown.", optional = true)
        long size) {

    public enum StorageType {
        CLOUDSTORAGE,
        URL,
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

    public static FileDetails fromUrl(final String url) {
        final String name = url.substring(url.lastIndexOf('/') + 1);
        return new FileDetails(name, url, StorageType.URL, "application/image", -1L);
    }
}
