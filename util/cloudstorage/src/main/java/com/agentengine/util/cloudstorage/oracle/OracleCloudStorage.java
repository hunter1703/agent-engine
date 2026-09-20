package com.agentengine.util.cloudstorage.oracle;

import static com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails.AccessType.ObjectRead;

import com.agentengine.util.cloudstorage.AbstractCloudStorageService;
import com.agentengine.util.cloudstorage.CloudStorageClientInfraConfig;
import com.agentengine.util.cloudstorage.CloudStorageServerInfraConfig;
import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.FileUtils.BucketKey;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.context.Context;
import com.agentengine.util.infra.InfraConfigService;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.auth.StringPrivateKeySupplier;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.model.CopyObjectDetails;
import com.oracle.bmc.objectstorage.model.CreateBucketDetails;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest;
import com.oracle.bmc.objectstorage.requests.CopyObjectRequest;
import com.oracle.bmc.objectstorage.requests.CreateBucketRequest;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetBucketRequest;
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
import java.util.function.Supplier;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OracleCloudStorage extends AbstractCloudStorageService {

  private static final Logger log = LoggerFactory.getLogger(OracleCloudStorage.class);

  private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";

  private final ObjectStorage client;
  private final String region;
  private final String namespace;
  private final String compartmentId;

  public OracleCloudStorage(final CloudStorageServerInfraConfig config, InfraConfigService infraConfigService) {
      super(infraConfigService);
      this.client = buildClient(config);
    this.region = config.getRegion();
    this.namespace = config.getNamespace();
    this.compartmentId = config.getCompartmentId();
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
            .bucketName(bucket())
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
        bucket() + "/" + key,
        FileDetails.StorageType.CLOUDSTORAGE,
        mediaType,
        contentLength);
  }

  @Override
  public Content download(final String source) {
    final BucketKey bucketKey = BucketKey.parse(source, bucket());
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
    final BucketKey bucketKey = BucketKey.parse(source, bucket());
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
    final BucketKey bucketKey = BucketKey.parse(source, bucket());
    client.deleteObject(
        DeleteObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(bucketKey.bucket())
            .objectName(bucketKey.key())
            .build());
  }

  @Override
  public String presignedGetUrl(final FileDetails fileDetails, final Duration validity) {
    final BucketKey bucketKey = BucketKey.parse(fileDetails.source(), bucket());
    final CreatePreauthenticatedRequestDetails requestDetails =
        CreatePreauthenticatedRequestDetails.builder()
            .name(ObjectId.get().toHexString())
            .bucketListingAction(PreauthenticatedRequest.BucketListingAction.Deny)
            .accessType(ObjectRead)
            .timeExpires(Date.from(Instant.now().plus(validity)))
            .objectName(bucketKey.key())
            .build();
    final CreatePreauthenticatedRequestResponse response =
        client.createPreauthenticatedRequest(
            CreatePreauthenticatedRequestRequest.builder()
                .namespaceName(namespace)
                .bucketName(bucketKey.bucket())
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
                  .bucketName(bucket())
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
    final BucketKey sourceBucketKey = BucketKey.parse(source.source(), bucket());
    final CopyObjectDetails copyObjectDetails =
        CopyObjectDetails.builder()
            .sourceObjectName(sourceBucketKey.key())
            .destinationRegion(region)
            .destinationNamespace(namespace)
            .destinationBucket(bucket())
            .destinationObjectName(destinationKey)
            .build();
    client.copyObject(
        CopyObjectRequest.builder()
            .namespaceName(namespace)
            .bucketName(sourceBucketKey.bucket())
            .copyObjectDetails(copyObjectDetails)
            .build());
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
      client.getBucket(
          GetBucketRequest.builder().namespaceName(namespace).bucketName(bucket).build());
    } catch (final BmcException exception) {
      if (exception.getStatusCode() != 404) {
        throw exception;
      }
      if (StringUtils.isBlank(compartmentId)) {
        throw new IllegalStateException(
            "Bucket '" + bucket + "' does not exist and the server has no compartmentId to create it in");
      }
      client.createBucket(
          CreateBucketRequest.builder()
              .namespaceName(namespace)
              .createBucketDetails(
                  CreateBucketDetails.builder().name(bucket).compartmentId(compartmentId).build())
              .build());
    }
  }

  @Override
  public void close() throws Exception {
    client.close();
  }

  private static ObjectStorage buildClient(final CloudStorageServerInfraConfig config) {
    final Region region = Region.fromRegionId(config.getRegion());
    final BasicAuthenticationDetailsProvider authProvider =
            config.isUseInstancePrincipal()
                    ? InstancePrincipalsAuthenticationDetailsProvider.builder().build()
                    : SimpleAuthenticationDetailsProvider.builder()
                    .tenantId(config.getTenantId())
                    .userId(config.getUserId())
                    .fingerprint(config.getFingerprint())
                    .privateKeySupplier(new StringPrivateKeySupplier(config.getPrivateKey()))
                    .passPhrase(config.getPassPhrase())
                    .region(region)
                    .build();
    return ObjectStorageClient.builder().region(region).build(authProvider);
  }
}
