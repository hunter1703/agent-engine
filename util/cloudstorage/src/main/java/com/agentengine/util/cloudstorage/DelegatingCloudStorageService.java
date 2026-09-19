package com.agentengine.util.cloudstorage;

import com.agentengine.util.common.beans.FileDetails;
import com.oracle.bmc.model.BmcException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

public final class DelegatingCloudStorageService implements CloudStorageService {
  private final CloudStorageService delegate;

  public DelegatingCloudStorageService(final CloudStorageService delegate) {
    this.delegate = delegate;
  }

  @Override
  public FileDetails upload(
      final String key,
      final String name,
      final InputStream inputStream,
      final long contentLength,
      final String mediaType,
      final Map<String, String> metadata) {
    return runWithErrorHandling(
        () -> delegate.upload(key, name, inputStream, contentLength, mediaType, metadata));
  }

  @Override
  public Content download(final String source) {
    return runWithErrorHandling(() -> delegate.download(source));
  }

  @Override
  public Content download(final FileDetails fileDetails) {
    return runWithErrorHandling(() -> delegate.download(fileDetails));
  }

  @Override
  public long getSize(final String source) {
    return runWithErrorHandling(() -> delegate.getSize(source));
  }

  @Override
  public void delete(final String source) {
    runWithErrorHandling(
        () -> {
          delegate.delete(source);
          return null;
        });
  }

  @Override
  public String presignedGetUrl(final FileDetails fileDetails, final Duration validity) {
    return runWithErrorHandling(() -> delegate.presignedGetUrl(fileDetails, validity));
  }

  @Override
  public List<String> list(final String keyPrefix) {
    return runWithErrorHandling(() -> delegate.list(keyPrefix));
  }

  @Override
  public FileDetails copy(
      final FileDetails source, final String name, final String destinationKey) {
    return runWithErrorHandling(() -> delegate.copy(source, name, destinationKey));
  }

  private static <T> T runWithErrorHandling(final Supplier<T> call) {
    try {
      return call.get();
    } catch (final AwsServiceException exception) {
      final String message =
          exception.awsErrorDetails() == null
              ? exception.getMessage()
              : exception.awsErrorDetails().errorMessage();
      throw new CloudStorageException(exception.statusCode(), message, exception);
    } catch (final BmcException exception) {
      throw new CloudStorageException(exception.getStatusCode(), exception.getMessage(), exception);
    }
  }

  @Override
  public void close() throws Exception {
    delegate.close();
  }
}
