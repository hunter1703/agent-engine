package com.agentengine.util.cloudstorage.s3;

import com.agentengine.util.cloudstorage.AbstractCloudStorageService;
import com.agentengine.util.cloudstorage.CloudStorageServerInfraConfig;
import com.agentengine.util.cloudstorage.CloudStorageService;
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
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * {@link CloudStorageService} backed by any S3-compatible object store (LocalStack for local dev,
 * AWS S3, or a provider's own S3-compatibility API).
 *
 * <p>Start LocalStack locally:
 *
 * <pre>
 *   docker run --rm -p 4566:4566 localstack/localstack
 * </pre>
 */
public class S3CloudStorage extends AbstractCloudStorageService {

  private static final Logger log = LoggerFactory.getLogger(S3CloudStorage.class);

  private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";

  private final S3Client s3;
  private final S3Presigner presigner;

  public S3CloudStorage(
      final CloudStorageServerInfraConfig config, final InfraConfigService infraConfigService) {
    super(infraConfigService);
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
              .bucket(bucket())
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
        name, bucket() + "/" + key, FileDetails.StorageType.CLOUDSTORAGE, mediaType, contentLength);
  }

  @Override
  public Content download(final String source) {
    final BucketKey bucketKey = BucketKey.parse(source, bucket());
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
    final BucketKey bucketKey = BucketKey.parse(source, bucket());
    return s3.headObject(
            HeadObjectRequest.builder().bucket(bucketKey.bucket()).key(bucketKey.key()).build())
        .contentLength();
  }

  @Override
  public void delete(final String source) {
    final BucketKey bucketKey = BucketKey.parse(source, bucket());
    s3.deleteObject(
        DeleteObjectRequest.builder().bucket(bucketKey.bucket()).key(bucketKey.key()).build());
  }

  @Override
  public String presignedGetUrl(final FileDetails fileDetails, final Duration validity) {
    final BucketKey bucketKey = BucketKey.parse(fileDetails.source(), bucket());
    return presigner
        .presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(
                    request -> request.bucket(bucketKey.bucket()).key(bucketKey.key()))
                .build())
        .url()
        .toString();
  }

  @Override
  public List<String> list(final String keyPrefix) {
    return s3
        .listObjectsV2Paginator(
            ListObjectsV2Request.builder().bucket(bucket()).prefix(keyPrefix).build())
        .stream()
        .flatMap(page -> page.contents().stream())
        .map(S3Object::key)
        .toList();
  }

  @Override
  public FileDetails copy(
      final FileDetails source, final String name, final String destinationKey) {
    final BucketKey sourceBucketKey = BucketKey.parse(source.source(), bucket());
    final CopyObjectRequest request =
        CopyObjectRequest.builder()
            .sourceBucket(sourceBucketKey.bucket())
            .sourceKey(sourceBucketKey.key())
            .destinationBucket(bucket())
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

  @Override
  public void ensureBucket(final String bucket) {
    try {
      s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
    } catch (final NoSuchBucketException exception) {
      s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    }
  }

  @Override
  public void close() {
    s3.close();
    presigner.close();
  }
}
