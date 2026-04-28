package com.agentengine.util.cloudstorage.localstack;

import com.agentengine.util.cloudstorage.CloudStorageInfraConfig;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.service.CloudStorageService;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * {@link CloudStorageService} backed by LocalStack (S3-compatible).
 *
 * <p>Configuration is loaded from the infra MongoDB store via {@link InfraConfigService}
 * using {@link CloudStorageInfraConfig#CATEGORY} / {@link CloudStorageInfraConfig#CONFIG_ID}.
 *
 * <p>Start LocalStack locally:
 * <pre>
 *   docker run --rm -p 4566:4566 localstack/localstack
 * </pre>
 */
@Singleton
public class LocalStackCloudStorageService implements CloudStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStackCloudStorageService.class);

    private static final String DEFAULT_MEDIA_TYPE = "application/octet-stream";

    private final InfraConfigService infraConfigService;
    private S3Client s3;
    private S3Presigner presigner;
    private volatile String defaultBucket;

    @Inject
    public LocalStackCloudStorageService(final InfraConfigService infraConfigService) {
        this.infraConfigService = infraConfigService;
    }

    @PostConstruct
    void init() {
        final CloudStorageInfraConfig config =
                infraConfigService.findById(CloudStorageInfraConfig.CATEGORY, CloudStorageInfraConfig.CONFIG_ID);

        if (config == null) {
            throw new IllegalStateException("LocalStack infra config not found: " + CloudStorageInfraConfig.CATEGORY
                    + ":" + CloudStorageInfraConfig.CONFIG_ID);
        }

        this.defaultBucket = config.getDefaultBucket();

        final StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey()));
        final URI endpoint = URI.create(config.getEndpointUrl());
        final Region region = Region.of(config.getRegion());

        this.s3 = S3Client.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .forcePathStyle(true)
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(endpoint)
                .region(region)
                .credentialsProvider(credentials)
                .build();

        ensureBucket();
        log.info("CloudStorageService initialised -> {} (bucket: {})", config.getEndpointUrl(), defaultBucket);
    }

    @Override
    public FileDetails upload(
            final String key, final String name, final InputStream inputStream, final long contentLength, String mediaType) {
        mediaType = StringUtils.isBlank(mediaType) ? DEFAULT_MEDIA_TYPE : mediaType;
        final RequestBody body = contentLength >= 0
                ? RequestBody.fromInputStream(inputStream, contentLength)
                : RequestBody.fromContentProvider(() -> inputStream, mediaType);
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(defaultBucket)
                        .key(key)
                        .contentType(mediaType)
                        .build(),
                body);
        return new FileDetails(
                name, defaultBucket + "/" + key, FileDetails.StorageType.CLOUDSTORAGE, mediaType, contentLength);
    }

    @Override
    public Content download(final String key) {
        final ResponseInputStream<GetObjectResponse> response = s3.getObject(GetObjectRequest.builder().bucket(defaultBucket).key(key).build());
        return new Content(response, response.response().contentType());
    }

    @Override
    public void delete(final String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(defaultBucket).key(key).build());
    }

    @Override
    public String presignedGetUrl(final FileDetails fileDetails, final Duration validity) {
        final String source = fileDetails.source();
        final int sep = source.indexOf('/');
        final String key = sep >= 0 ? source.substring(sep + 1) : source;
        return presigner
                .presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(validity)
                        .getObjectRequest(request -> request.bucket(defaultBucket).key(key))
                        .build())
                .url()
                .toString();
    }

    @Override
    public List<String> list(final String keyPrefix) {
        return s3.listObjectsV2Paginator(
                        ListObjectsV2Request.builder().bucket(defaultBucket).prefix(keyPrefix).build())
                .stream()
                .flatMap(page -> page.contents().stream())
                .map(S3Object::key)
                .toList();
    }

    @Override
    public void copy(final String sourceKey, final String destinationKey) {
        s3.copyObject(CopyObjectRequest.builder()
                .sourceBucket(defaultBucket)
                .sourceKey(sourceKey)
                .destinationBucket(defaultBucket)
                .destinationKey(destinationKey)
                .build());
    }

    private void ensureBucket() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(defaultBucket).build());
        } catch (NoSuchBucketException e) {
            log.info("Creating bucket: {}", defaultBucket);
            s3.createBucket(CreateBucketRequest.builder().bucket(defaultBucket).build());
        }
    }
}
