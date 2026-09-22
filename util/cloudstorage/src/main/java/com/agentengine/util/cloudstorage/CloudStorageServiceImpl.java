package com.agentengine.util.cloudstorage;

import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.context.Context;
import com.oracle.bmc.model.BmcException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

@SuppressWarnings("resource")
@Singleton
public class CloudStorageServiceImpl implements CloudStorageService {

  private final CloudStorageServiceFactory factory;

  @Inject
  public CloudStorageServiceImpl(final CloudStorageServiceFactory factory) {
    this.factory = factory;
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
        () -> service().upload(key, name, inputStream, contentLength, mediaType, metadata));
  }

  @Override
  public Content download(final String source) {
    return runWithErrorHandling(() -> service().download(source));
  }

  @Override
  public Content download(final FileDetails fileDetails) {
    return runWithErrorHandling(() -> service().download(fileDetails));
  }

  @Override
  public long getSize(final String source) {
    return runWithErrorHandling(() -> service().getSize(source));
  }

  @Override
  public void delete(final String source) {
    runWithErrorHandling(
        () -> {
          service().delete(source);
          return null;
        });
  }

  @Override
  public String presignedGetUrl(final FileDetails fileDetails, final Duration validity) {
    return runWithErrorHandling(() -> service().presignedGetUrl(fileDetails, validity));
  }

  @Override
  public List<String> list(final String keyPrefix) {
    return runWithErrorHandling(() -> service().list(keyPrefix));
  }

  @Override
  public FileDetails copy(
      final FileDetails source, final String name, final String destinationKey) {
    return runWithErrorHandling(() -> service().copy(source, name, destinationKey));
  }

  @Override
  public void ensureBucket(final String bucket) {
    runWithErrorHandling(
        () -> {
          service().ensureBucket(bucket);
          return null;
        });
  }

  @Override
  public void close() throws Exception {
    factory.closeAll();
  }

  private CloudStorageService service() {
    return factory.get(Context.requireCustomerId());
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
}
