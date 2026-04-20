package com.agentengine.util.cloudstorage;

import java.time.Instant;

/**
 * Immutable metadata record for an object stored in cloud storage.
 *
 * @param bucket       bucket that contains the object
 * @param key          object key within the bucket
 * @param sizeBytes    content length in bytes
 * @param mediaType    MIME type reported by the storage backend
 * @param etag         entity tag (maybe null if the backend does not provide one)
 * @param lastModified last-modified timestamp (maybe null)
 */
public record StoredObject(String bucket, String key, long sizeBytes, String mediaType, String etag, Instant lastModified) {
}
