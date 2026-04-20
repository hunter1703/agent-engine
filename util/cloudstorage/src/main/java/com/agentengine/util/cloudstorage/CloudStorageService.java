package com.agentengine.util.cloudstorage;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Provider-agnostic object storage abstraction.
 *
 * <p>Implementations must be thread-safe. All operations are synchronous and blocking; callers
 * should dispatch to a virtual thread when latency matters.
 */
public interface CloudStorageService {

    /**
     * Uploads a local file to the given bucket and key.
     *
     * @param bucket     target bucket name
     * @param key        object key (path within the bucket)
     * @param sourcePath local file to upload
     * @param mediaType  MIME type, e.g. {@code "image/jpeg"}
     * @return metadata describing the stored object
     */
    StoredObject upload(String bucket, String key, Path sourcePath, String mediaType);

    /**
     * Uploads a raw stream to the given bucket and key.
     *
     * @param bucket        target bucket name
     * @param key           object key
     * @param inputStream   data to upload
     * @param contentLength byte length of the stream; pass {@code -1} if unknown
     * @param mediaType     MIME type
     * @return metadata describing the stored object
     */
    StoredObject upload(String bucket, String key, InputStream inputStream, long contentLength, String mediaType);

    /**
     * Downloads an object to a local file.
     *
     * @param bucket      source bucket name
     * @param key         object key
     * @param destination local path to write the object to
     */
    void download(String bucket, String key, Path destination);

    /**
     * Opens a streaming read of an object without writing it to disk.
     *
     * @param bucket source bucket name
     * @param key    object key
     * @return open {@link InputStream}; caller is responsible for closing it
     */
    InputStream openStream(String bucket, String key);

    /**
     * Deletes an object.
     *
     * @param bucket bucket name
     * @param key    object key
     */
    void delete(String bucket, String key);

    /**
     * Returns metadata for an object without downloading its content.
     *
     * @param bucket bucket name
     * @param key    object key
     * @return metadata, or {@code null} if the object does not exist
     */
    StoredObject stat(String bucket, String key);

    /**
     * Generates a pre-signed URL that allows unauthenticated GET access for the given duration.
     *
     * @param bucket   bucket name
     * @param key      object key
     * @param validity how long the URL should remain valid
     * @return pre-signed URL string
     */
    String presignedGetUrl(String bucket, String key, Duration validity);

    /**
     * Ensures a bucket exists, creating it if necessary.
     *
     * @param bucket bucket name
     */
    void ensureBucket(String bucket);
}
