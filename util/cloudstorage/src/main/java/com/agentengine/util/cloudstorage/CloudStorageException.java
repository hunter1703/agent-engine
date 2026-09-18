package com.agentengine.util.cloudstorage;

/** A storage provider call that failed, with the HTTP status the provider answered with. */
public class CloudStorageException extends RuntimeException {
  private final int statusCode;

  public CloudStorageException(final int statusCode, final String message, final Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public int getStatusCode() {
    return statusCode;
  }
}
