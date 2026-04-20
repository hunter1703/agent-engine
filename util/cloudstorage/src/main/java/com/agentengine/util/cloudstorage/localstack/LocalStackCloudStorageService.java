package com.agentengine.util.cloudstorage.localstack;

import com.agentengine.util.cloudstorage.CloudStorageService;
import com.agentengine.util.cloudstorage.StoredObject;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * {@link CloudStorageService} backed by LocalStack (S3-compatible).
 *
 * <p>Configuration is loaded from the infra MongoDB store via {@link InfraConfigService}
 * using {@link LocalStackInfraConfig#CATEGORY} / {@link LocalStackInfraConfig#CONFIG_ID}.
 *
 * <p>Start LocalStack locally:
 * <pre>
 *   docker run --rm -p 4566:4566 localstack/localstack
 * </pre>
 */
@Singleton
public class LocalStackCloudStorageService implements CloudStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStackCloudStorageService.class);

    private final InfraConfigService infraConfigService;
    private S3Client s3;
    private S3Presigner presigner;
    private String defaultBucket;

    @Inject
    public LocalStackCloudStorageService(final InfraConfigService infraConfigService) {
        this.infraConfigService = infraConfigService;
    }

    @PostConstruct
    void init() {
        final LocalStackInfraConfig config = infraConfigService.findById(
                LocalStackInfraConfig.CATEGORY, LocalStackInfraConfig.CONFIG_ID);

        if (config == null) {
            throw new IllegalStateException(
                    "LocalStack infra config not found: " + LocalStackInfraConfig.CATEGORY
                    + ":" + LocalStackInfraConfig.CONFIG_ID);
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

        ensureBucket(defaultBucket);
        log.info("CloudStorageService initialised -> {} (bucket: {})", config.getEndpointUrl(), defaultBucket);
    }

    @Override
    public StoredObject upload(
            final String bucket, final String key, final Path sourcePath, final String mediaType) {
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(mediaType).build(),
                sourcePath);
        return stat(bucket, key);
    }

    @Override
    public StoredObject upload(
            final String bucket,
            final String key,
            final InputStream inputStream,
            final long contentLength,
            final String mediaType) {
        final RequestBody body = contentLength >= 0
                ? RequestBody.fromInputStream(inputStream, contentLength)
                : RequestBody.fromContentProvider(() -> inputStream, mediaType);
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(mediaType).build(),
                body);
        return stat(bucket, key);
    }

    @Override
    public void download(final String bucket, final String key, final Path destination) {
        s3.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build(),
                ResponseTransformer.toFile(destination));
    }

    @Override
    public InputStream openStream(final String bucket, final String key) {
        return s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void delete(final String bucket, final String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public StoredObject stat(final String bucket, final String key) {
        try {
            final HeadObjectResponse head = s3.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return new StoredObject(
                    bucket,
                    key,
                    head.contentLength() != null ? head.contentLength() : -1,
                    head.contentType(),
                    head.eTag(),
                    head.lastModified());
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public String presignedGetUrl(final String bucket, final String key, final Duration validity) {
        return presigner
                .presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(validity)
                        .getObjectRequest(r -> r.bucket(bucket).key(key))
                        .build())
                .url()
                .toString();
    }

    @Override
    public void ensureBucket(final String bucket) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            log.info("Creating bucket: {}", bucket);
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }
}
