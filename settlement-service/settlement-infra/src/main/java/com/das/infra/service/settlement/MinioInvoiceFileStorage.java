package com.das.infra.service.settlement;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.IInvoiceFileStorage;
import com.das.cleanddd.domain.settlement.entities.InvoiceFile;
import com.das.cleanddd.domain.settlement.entities.InvoiceId;
import com.das.infra.service.settlement.LocalDiskInvoiceFileStorage.InvoiceFileStorageException;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;

/**
 * S3-compatible file storage backed by MinIO (or AWS S3, GCS, Azure Blob via
 * the S3 gateway).
 *
 * <p>Activated only when the Spring profile {@code minio} is active, e.g.:
 * {@code SPRING_PROFILES_ACTIVE=prod,minio}. In that case it takes over as
 * {@code @Primary} over {@link LocalDiskInvoiceFileStorage}.</p>
 *
 * <p>Required application properties (or env vars):
 * <pre>
 *   minio.endpoint   = http://minio:9000        (MINIO_ENDPOINT)
 *   minio.access-key = minioadmin               (MINIO_ACCESS_KEY)
 *   minio.secret-key = minioadmin               (MINIO_SECRET_KEY)
 *   minio.bucket     = invoice-files            (MINIO_BUCKET)
 * </pre>
 * </p>
 *
 * <p>Object key layout: {@code <invoiceId>/<fileName>}</p>
 */
@Profile("minio")
@Primary
@Service
public class MinioInvoiceFileStorage implements IInvoiceFileStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioInvoiceFileStorage.class);

    private final MinioClient client;
    private final String bucket;

    public MinioInvoiceFileStorage(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket}") String bucket) {

        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        ensureBucketExists();
        log.info("MinIO invoice file storage — endpoint: {}, bucket: {}", endpoint, bucket);
    }

    @Override
    public void store(InvoiceId invoiceId, InvoiceFile file) {
        String key = objectKey(invoiceId, file.fileName());
        try {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .stream(new ByteArrayInputStream(file.content()), file.content().length, -1)
                            .contentType(file.contentType())
                            .build());
            log.debug("Stored invoice file in MinIO: {}", key);
        } catch (Exception e) {
            throw new InvoiceFileStorageException(
                    "Failed to store invoice file for invoice " + invoiceId.value(), e);
        }
    }

    @Override
    public Optional<byte[]> loadContent(InvoiceId invoiceId, String fileName, String expectedHash) {
        String key = objectKey(invoiceId, fileName);
        try {
            byte[] bytes = client.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build())
                    .readAllBytes();

            String actualHash = computeSha256(bytes);
            if (!actualHash.equals(expectedHash)) {
                throw new FileIntegrityException(
                        "Invoice file integrity check failed for invoice " + invoiceId.value()
                        + " — file may be corrupted or tampered with.");
            }
            log.debug("Invoice file integrity verified in MinIO: {}", key);
            return Optional.of(bytes);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.warn("Invoice file not found in MinIO: {}", key);
                return Optional.empty();
            }
            throw new InvoiceFileStorageException(
                    "Failed to load invoice file for invoice " + invoiceId.value(), e);
        } catch (IOException | RuntimeException e) {
            throw new InvoiceFileStorageException(
                    "Failed to load invoice file for invoice " + invoiceId.value(), e);
        } catch (Exception e) {
            throw new InvoiceFileStorageException(
                    "Failed to load invoice file for invoice " + invoiceId.value(), e);
        }
    }

    @Override
    public void delete(InvoiceId invoiceId, String fileName) {
        String key = objectKey(invoiceId, fileName);
        try {
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(key)
                            .build());
            log.debug("Deleted invoice file from MinIO: {}", key);
        } catch (Exception e) {
            log.warn("Could not delete invoice file {} from MinIO: {}", key, e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String objectKey(InvoiceId invoiceId, String fileName) {
        return invoiceId.value() + "/" + fileName;
    }

    private void ensureBucketExists() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot connect to MinIO or create bucket '" + bucket + "'", e);
        }
    }

    private static String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
