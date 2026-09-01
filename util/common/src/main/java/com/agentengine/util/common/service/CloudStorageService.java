package com.agentengine.util.common.service;

import com.agentengine.util.common.beans.FileDetails;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CloudStorageService {

  /**
   * Uploads a stream to a caller-supplied storage key; the returned source is {@code bucket/key}.
   */
  default FileDetails upload(
      String key, String name, InputStream inputStream, long contentLength, String mediaType) {
    return upload(key, name, inputStream, contentLength, mediaType, null);
  }

  FileDetails upload(
      String key,
      String name,
      InputStream inputStream,
      long contentLength,
      String mediaType,
      Map<String, String> metadata);

  /** Uploads a stream to a randomly generated key. */
  default FileDetails upload(
      String name, InputStream inputStream, long contentLength, String mediaType) {
    return upload(
        UUID.randomUUID().toString().replace("-", ""), name, inputStream, contentLength, mediaType);
  }

  /** Downloads the object at {@code key}, returning its byte stream and content type. */
  Content download(String key);

  Content download(FileDetails fileDetails);

  void delete(String key);

  default void delete(FileDetails fileDetails) {
    final String source = fileDetails.source();
    final int sep = source.indexOf('/');
    delete(source.substring(sep + 1));
  }

  String presignedGetUrl(FileDetails fileDetails, Duration validity);

  /**
   * Lists all object keys whose paths begin with {@code keyPrefix}. Returns raw storage keys (not
   * bucket-prefixed).
   */
  List<String> list(String keyPrefix);

  FileDetails copy(FileDetails source, String name, String destinationKey);

  record Content(InputStream stream, String mimeType) {}
}
