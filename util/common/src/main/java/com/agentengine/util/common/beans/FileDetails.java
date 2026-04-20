package com.agentengine.util.common.beans;

import com.agentengine.util.cloudstorage.CloudStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Generic file reference that is storage-backend agnostic.
 *
 * <p>For {@link StorageType#CLOUDSTORAGE}, {@code path} is {@code <bucket>/<key>}.
 *
 * @param path      storage-specific path to the file
 * @param type      storage backend type
 * @param mimeType  optional MIME type (e.g. {@code "image/jpeg"})
 * @param size      optional file size in bytes; {@code -1} if unknown
 */
public record FileDetails(
        String name, String path, StorageType type, String mimeType, long size, String base64Content) {

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

    public FileDetails resolved(final CloudStorageService cloudStorageService) {
        try (final InputStream downloadedStream = cloudStorageService.download(this)) {
            final String base64Content = Base64.getEncoder().encodeToString(downloadedStream.readAllBytes());
            return new FileDetails(name, path, type, mimeType, size, base64Content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
