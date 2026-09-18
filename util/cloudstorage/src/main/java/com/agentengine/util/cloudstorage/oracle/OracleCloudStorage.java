package com.agentengine.util.cloudstorage.oracle;

import static com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails.AccessType.ObjectRead;

import com.agentengine.util.cloudstorage.CloudStorageInfraConfig;
import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.cloudstorage.CloudStorageServiceProducer;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.FileUtils.BucketKey;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.infra.InfraConfigService;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.StringPrivateKeySupplier;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CopyObjectDetails;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest;
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.HeadObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.HeadObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CloudStorageService} backed by OCI Object Storage's own native SDK, rather than its
 * S3-compatibility API — avoiding compatibility friction the AWS SDK has there (aws-chunked upload
 * encoding, strict request-length checks against a live request body stream) and supporting
 * Instance Principal auth, which the S3-compat API cannot.
 *
 * <p>Constructed directly by {@link CloudStorageServiceProducer} rather than injected — not a CDI
 * bean itself, since which {@link CloudStorageService} implementation backs a given deployment is a
 * runtime config choice, not a compile-time one.
 */
public class OracleCloudStorage implements CloudStorageService {

  private static final Logger log = LoggerFactory.getLogger(OracleCloudStorage.class);

  private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";

  private final ObjectStorage client;
  private final String region;
  private final String namespace;
  private final String defaultBucket;

  public OracleCloudStorage(final InfraConfigService infraConfigService) {
    final CloudStorageInfraConfig config =
        infraConfigService.findById(
            CloudStorageInfraConfig.CATEGORY,
            CloudStorageInfraConfig.TYPE,
            CloudStorageInfraConfig.CONFIG_ID);
    this.client = buildClient(config);
    this.region = config.getRegion();
    this.namespace = config.getNamespace();
    this.defaultBucket = config.getDefaultBucket();
  }

  private static ObjectStorage buildClient(final CloudStorageInfraConfig cloudStorageInfraConfig) {
    final Region region = Region.fromRegionId(cloudStorageInfraConfig.getRegion());
    final BasicAuthenticationDetailsProvider authProvider =
        cloudStorageInfraConfig.isUseInstancePrincipal()
            ? InstancePrincipalsAuthenticationDetailsProvider.builder().build()
            : SimpleAuthenticationDetailsProvider.builder()
                .tenantId(cloudStorageInfraConfig.getTenantId())
                .userId(cloudStorageInfraConfig.getUserId())
                .fingerprint(cloudStorageInfraConfig.getFingerprint())
                .privateKeySupplier(
                    new StringPrivateKeySupplier(cloudStorageInfraConfig.getPrivateKey()))
                .passPhrase(cloudStorageInfraConfig.getPassPhrase())
                .region(region)
                .build();
    return ObjectStorageClient.builder().region(region).build(authProvider);
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
    final PutObjectRequest.Builder requestBuilder =
        PutObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(defaultBucket)
            .objectName(key)
            .contentType(mediaType)
            .opcMeta(CollectionUtils.nullSafeMap(metadata))
            .putObjectBody(inputStream);
    if (contentLength >= 0) {
      requestBuilder.contentLength(contentLength);
    }
    try {
      client.putObject(requestBuilder.build());
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
    final GetObjectResponse response =
        client.getObject(
            GetObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketKey.bucket())
                .objectName(bucketKey.key())
                .build());
    return new Content(response.getInputStream(), response.getContentType());
  }

  @Override
  public Content download(final FileDetails fileDetails) {
    return download(fileDetails.source());
  }

  @Override
  public long getSize(final String source) {
    final BucketKey bucketKey = BucketKey.parse(source, defaultBucket);
    final HeadObjectResponse response =
        client.headObject(
            HeadObjectRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketKey.bucket())
                .objectName(bucketKey.key())
                .build());
    return response.getContentLength();
  }

  @Override
  public void delete(final String source) {
    final BucketKey bucketKey = BucketKey.parse(source, defaultBucket);
    client.deleteObject(
        DeleteObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(bucketKey.bucket())
            .objectName(bucketKey.key())
            .build());
  }

  @Override
  public String presignedGetUrl(final FileDetails fileDetails, final Duration validity) {
    final String source = fileDetails.source();
    final int sep = source.indexOf('/');
    final String key = sep >= 0 ? source.substring(sep + 1) : source;
    final CreatePreauthenticatedRequestDetails requestDetails =
        CreatePreauthenticatedRequestDetails.builder()
            .name(ObjectId.get().toHexString())
            .bucketListingAction(PreauthenticatedRequest.BucketListingAction.Deny)
            .accessType(ObjectRead)
            .timeExpires(Date.from(Instant.now().plus(validity)))
            .objectName(key)
            .build();
    final CreatePreauthenticatedRequestResponse response =
        client.createPreauthenticatedRequest(
            CreatePreauthenticatedRequestRequest.builder()
                .namespaceName(namespace)
                .bucketName(defaultBucket)
                .createPreauthenticatedRequestDetails(requestDetails)
                .build());
    return "https://objectstorage."
        + region
        + ".oraclecloud.com"
        + response.getPreauthenticatedRequest().getAccessUri();
  }

  @Override
  public List<String> list(final String keyPrefix) {
    final List<String> keys = new ArrayList<>();
    String startAfter = null;
    ListObjectsResponse response;
    do {
      response =
          client.listObjects(
              ListObjectsRequest.builder()
                  .namespaceName(namespace)
                  .bucketName(defaultBucket)
                  .prefix(keyPrefix)
                  .start(startAfter)
                  .build());
      for (final ObjectSummary object :
          CollectionUtils.nullSafeList(response.getListObjects().getObjects())) {
        keys.add(object.getName());
      }
      startAfter = response.getListObjects().getNextStartWith();
    } while (startAfter != null);
    return keys;
  }

  @Override
  public FileDetails copy(
      final FileDetails source, final String name, final String destinationKey) {
    // OCI's copyObject is asynchronous - it starts a work request and returns immediately, so
    // the destination object may not exist yet by the time this method returns.
    final CopyObjectDetails copyObjectDetails =
        CopyObjectDetails.builder()
            .sourceObjectName(source.source())
            .destinationRegion(region)
            .destinationNamespace(namespace)
            .destinationBucket(defaultBucket)
            .destinationObjectName(destinationKey)
            .build();
    client.copyObject(
        CopyObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(defaultBucket)
            .copyObjectDetails(copyObjectDetails)
            .build());
    return new FileDetails(
        name,
        destinationKey,
        FileDetails.StorageType.CLOUDSTORAGE,
        source.mimeType(),
        source.size());
  }
}
