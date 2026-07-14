package co.za.tveco.bff.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class R2DocumentStorageService {

    private final boolean enabled;
    private final String bucket;
    private final Duration uploadUrlTtl;
    private final Duration downloadUrlTtl;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public R2DocumentStorageService(
            @Value("${app.storage.r2.enabled:false}") boolean enabled,
            @Value("${app.storage.r2.endpoint:}") String endpoint,
            @Value("${app.storage.r2.access-key-id:}") String accessKeyId,
            @Value("${app.storage.r2.secret-access-key:}") String secretAccessKey,
            @Value("${app.storage.r2.bucket:}") String bucket,
            @Value("${app.storage.r2.region:auto}") String region,
            @Value("${app.storage.r2.upload-url-expiration-seconds:900}") long uploadUrlExpirationSeconds,
            @Value("${app.storage.r2.download-url-expiration-seconds:300}") long downloadUrlExpirationSeconds
    ) {
        this.enabled = enabled;
        this.bucket = bucket == null ? "" : bucket.trim();
        this.uploadUrlTtl = Duration.ofSeconds(uploadUrlExpirationSeconds);
        this.downloadUrlTtl = Duration.ofSeconds(downloadUrlExpirationSeconds);

        if (!enabled) {
            this.s3Client = null;
            this.s3Presigner = null;
            return;
        }

        if (isBlank(endpoint) || isBlank(accessKeyId) || isBlank(secretAccessKey) || isBlank(this.bucket)) {
            throw new IllegalArgumentException("R2 is enabled but storage configuration is incomplete");
        }

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId.trim(), secretAccessKey.trim());
        Region awsRegion = Region.of((region == null || region.isBlank()) ? "auto" : region.trim());
        URI endpointUri = URI.create(endpoint.trim());
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .checksumValidationEnabled(false)
                .build();

        this.s3Client = S3Client.builder()
                .endpointOverride(endpointUri)
                .region(awsRegion)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();

        this.s3Presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .region(awsRegion)
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
    }

    public boolean isConfigured() {
        return enabled;
    }

    public String bucket() {
        return bucket;
    }

    public R2PresignedUpload createUpload(String objectKey, String mimeType) {
        assertConfigured();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(mimeType)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(uploadUrlTtl)
                        .putObjectRequest(putObjectRequest)
                        .build()
        );

        return new R2PresignedUpload(
                presigned.url().toString(),
                Instant.now().plus(uploadUrlTtl),
                Map.of("Content-Type", mimeType)
        );
    }

    public HeadObjectResponse headObject(String objectKey) {
        assertConfigured();
        return s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build());
    }

    public R2PresignedDownload createDownload(String objectKey, String fileName) {
        assertConfigured();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(downloadUrlTtl)
                        .getObjectRequest(builder -> builder
                                .bucket(bucket)
                                .key(objectKey)
                                .responseContentDisposition("attachment; filename=\"" + sanitizeHeaderValue(fileName) + "\"")
                        )
                        .build()
        );

        return new R2PresignedDownload(
                presigned.url().toString(),
                Instant.now().plus(downloadUrlTtl)
        );
    }

    private void assertConfigured() {
        if (!enabled || s3Client == null || s3Presigner == null) {
            throw new IllegalArgumentException("Document storage is not configured");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String sanitizeHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return "download";
        }
        return value.replace("\r", "").replace("\n", "").replace("\"", "'");
    }
}