package com.agentengine.util.cloudstorage.s3;

import com.agentengine.util.cloudstorage.CloudStorageInfraConfig;
import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.cloudstorage.CloudStorageServiceProducer;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.FileUtils.BucketKey;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.infra.InfraConfigService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * {@link CloudStorageService} backed by any S3-compatible object store (LocalStack for local dev,
 * AWS S3, or a provider's own S3-compatibility API).
 *
 * <p>Configuration is loaded from the infra MongoDB store via {@link InfraConfigService} using
 * {@link CloudStorageInfraConfig#CATEGORY} / {@link CloudStorageInfraConfig#CONFIG_ID}.
 *
 * <p>Start LocalStack locally:
 *
 * <pre>
 *   docker run --rm -p 4566:4566 localstack/localstack
 * </pre>
 *
 * <p>Constructed directly by {@link CloudStorageServiceProducer} rather than injected — not a CDI
 * bean itself, since which {@link CloudStorageService} implementation backs a given deployment is a
 * runtime config choice, not a compile-time one.
 */
public class S3CloudStorage implements CloudStorageService {

  private static final Logger log = LoggerFactory.getLogger(S3CloudStorage.class);

  private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";

  private final S3Client s3;
  private final S3Presigner presigner;
  private final String defaultBucket;

  public S3CloudStorage(final InfraConfigService infraConfigService) {
    final CloudStorageInfraConfig config =
        infraConfigService.findById(
            CloudStorageInfraConfig.CATEGORY,
            CloudStorageInfraConfig.TYPE,
            CloudStorageInfraConfig.CONFIG_ID);
    final StaticCredentialsProvider credentials =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey()));
    final URI endpoint = URI.create(config.getEndpointUrl());
    final Region region = Region.of(config.getRegion());

    this.s3 =
        S3Client.builder()
            .endpointOverride(endpoint)
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(config.isPathStyleAccess())
                    .chunkedEncodingEnabled(config.isChunkedEncodingEnabled())
                    .build())
            .build();
    this.presigner =
        S3Presigner.builder()
            .endpointOverride(endpoint)
            .region(region)
            .credentialsProvider(credentials)
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(config.isPathStyleAccess())
                    .build())
            .build();
    this.defaultBucket = config.getDefaultBucket();
  }

  @Override
  public FileDetails upload(
      final String key,
      final String name,
      final InputStream inputStream,
      final long contentLength,
      String mediaType,
      final Map<String, String> metadata) {
    mediaType = StringUtils.isBlank(mediaType) ? DEFAULT_MEDIA_TYPE : mediaType;
    // RequestBody.fromInputStream(stream, length) assumes the stream hands back every promised
    // byte across however many read() calls it takes, but a JAX-RS request body stream can
    // legitimately return a partial read while more data is still arriving over the wire,
    // which the SDK's stricter length check then rejects. Reading it fully first avoids that.
    final byte[] bytes;
    try {
      bytes = inputStream.readAllBytes();
    } catch (final IOException ex) {
      throw new UncheckedIOException("Failed to read upload content", ex);
    }
    final RequestBody body = RequestBody.fromBytes(bytes);
    try {
      s3.putObject(
          PutObjectRequest.builder()
              .bucket(defaultBucket)
              .key(key)
              .contentType(mediaType)
              .metadata(CollectionUtils.nullSafeMap(metadata))
              .build(),
          body);
    } catch (final Exception ex) {
      log.error(
          "putObject failed: exceptionClass={} message={}",
          ex.getClass().getName(),
          ex.getMessage(),
          ex);
      throw ex;
    }
    return new FileDetails(
        name,
        defaultBucket + "/" + key,
        FileDetails.StorageType.CLOUDSTORAGE,
        mediaType,
        contentLength);
  }

  @Override
  public Content download(final String source) {
    final BucketKey bucketKey = BucketKey.parse(source, defaultBucket);
    final ResponseInputStream<GetObjectResponse> response =
        s3.getObject(
            GetObjectRequest.builder().bucket(bucketKey.bucket()).key(bucketKey.key()).build());
    return new Content(response, response.response().contentType());
  }

  @Override
  public Content download(final FileDetails fileDetails) {
    return download(fileDetails.source());
  }

  @Override
  public long getSize(final String source) {
    final BucketKey bucketKey = BucketKey.parse(source, defaultBucket);
    return s3.headObject(
            HeadObjectRequest.builder().bucket(bucketKey.bucket()).key(bucketKey.key()).build())
        .contentLength();
  }

  @Override
  public void delete(final String source) {
    final BucketKey bucketKey = BucketKey.parse(source, defaultBucket);
    s3.deleteObject(
        DeleteObjectRequest.builder().bucket(bucketKey.bucket()).key(bucketKey.key()).build());
  }

  @Override
  public String presignedGetUrl(final FileDetails fileDetails, final Duration validity) {
    final String source = fileDetails.source();
    final int sep = source.indexOf('/');
    final String key = sep >= 0 ? source.substring(sep + 1) : source;
    return presigner
        .presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(request -> request.bucket(defaultBucket).key(key))
                .build())
        .url()
        .toString();
  }

  @Override
  public List<String> list(final String keyPrefix) {
    return s3
        .listObjectsV2Paginator(
            ListObjectsV2Request.builder().bucket(defaultBucket).prefix(keyPrefix).build())
        .stream()
        .flatMap(page -> page.contents().stream())
        .map(S3Object::key)
        .toList();
  }

  @Override
  public FileDetails copy(
      final FileDetails source, final String name, final String destinationKey) {
    final CopyObjectRequest request =
        CopyObjectRequest.builder()
            .sourceBucket(defaultBucket)
            .sourceKey(source.source())
            .destinationBucket(defaultBucket)
            .destinationKey(destinationKey)
            .build();
    s3.copyObject(request);
    return new FileDetails(
        name,
        destinationKey,
        FileDetails.StorageType.CLOUDSTORAGE,
        source.mimeType(),
        source.size());
  }
}
