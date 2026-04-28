package com.agentengine.util.common.service;

import com.agentengine.util.common.beans.FileDetails;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

public interface CloudStorageService {

    /** Uploads a stream to a caller-supplied storage key; the returned source is {@code bucket/key}. */
    FileDetails upload(String key, String name, InputStream inputStream, long contentLength, String mediaType);

    /** Uploads a stream to a randomly generated key. */
    default FileDetails upload(String name, InputStream inputStream, long contentLength, String mediaType) {
        return upload(UUID.randomUUID().toString().replace("-", ""), name, inputStream, contentLength, mediaType);
    }

    /** Downloads the object at {@code key}, returning its byte stream and content type. */
    Content download(String key);

    /** Deletes the object at {@code key}. */
    void delete(String key);

    /** Deletes the object referenced by {@code fileDetails}. */
    default void delete(FileDetails fileDetails) {
        final String source = fileDetails.source();
        final int sep = source.indexOf('/');
        delete(source.substring(sep + 1));
    }

    String presignedGetUrl(FileDetails fileDetails, Duration validity);

    /**
     * Lists all object keys whose paths begin with {@code keyPrefix}.
     * Returns raw storage keys (not bucket-prefixed).
     */
    List<String> list(String keyPrefix);

    void copy(String sourceKey, String destinationKey);

    record Content(InputStream stream, String mimeType) {}
}
