package com.das.inframysql.service.settlement;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.settlement.entities.IInvoiceFileStorage;
import com.das.cleanddd.domain.settlement.entities.InvoiceFile;
import com.das.cleanddd.domain.settlement.entities.InvoiceId;

/**
 * Stores invoice digital files on the local file system.
 *
 * <p>Each file is written to: {@code <invoice.file.storage.path>/<invoiceId>/<sanitizedFileName>}
 * The directory is created on demand. The file name is sanitized to prevent path-traversal
 * attacks: only the last path component is used and sequences of {@code ..} are removed.</p>
 */
@Service
public class LocalDiskInvoiceFileStorage implements IInvoiceFileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskInvoiceFileStorage.class);

    private final Path storageRoot;

    public LocalDiskInvoiceFileStorage(
            @Value("${invoice.file.storage.path}") String storagePath) {
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        log.info("Invoice file storage root: {}", this.storageRoot);
    }

    @Override
    public void store(InvoiceId invoiceId, InvoiceFile file) {
        Path target = resolveStoragePath(invoiceId, file.fileName());
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, file.content(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("Stored invoice file: {}", target);
        } catch (IOException e) {
            throw new InvoiceFileStorageException(
                    "Failed to store invoice file for invoice " + invoiceId.value(), e);
        }
    }

    @Override
    public Optional<byte[]> loadContent(InvoiceId invoiceId, String fileName, String expectedHash) {
        Path target = resolveStoragePath(invoiceId, fileName);
        if (!Files.exists(target)) {
            log.warn("Invoice file not found on disk: {}", target);
            return Optional.empty();
        }
        try {
            byte[] bytes = Files.readAllBytes(target);
            String actualHash = computeSha256(bytes);
            if (!actualHash.equals(expectedHash)) {
                throw new IInvoiceFileStorage.FileIntegrityException(
                        "Invoice file integrity check failed for invoice " + invoiceId.value()
                        + " — file may be corrupted or tampered with.");
            }
            log.debug("Invoice file integrity verified: {}", target);
            return Optional.of(bytes);
        } catch (IOException e) {
            throw new InvoiceFileStorageException(
                    "Failed to load invoice file for invoice " + invoiceId.value(), e);
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

    @Override
    public void delete(InvoiceId invoiceId, String fileName) {
        Path target = resolveStoragePath(invoiceId, fileName);
        try {
            Files.deleteIfExists(target);
            log.debug("Deleted invoice file: {}", target);
        } catch (IOException e) {
            log.warn("Could not delete invoice file {}: {}", target, e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Resolves and validates the storage path for a given invoice and file name.
     * Prevents path-traversal by stripping directory separators from the file name
     * and verifying the resolved path stays within the storage root.
     */
    private Path resolveStoragePath(InvoiceId invoiceId, String fileName) {
        String safeFileName = Paths.get(fileName).getFileName().toString()
                .replaceAll("\\.\\.", "");
        Path resolved = storageRoot
                .resolve(invoiceId.value())
                .resolve(safeFileName)
                .normalize();

        if (!resolved.startsWith(storageRoot)) {
            throw new InvoiceFileStorageException(
                    "Illegal file path detected — possible path traversal attempt.");
        }
        return resolved;
    }

    // ── Internal exception ─────────────────────────────────────────────────

    public static class InvoiceFileStorageException extends RuntimeException {
        public InvoiceFileStorageException(String message) {
            super(message);
        }
        public InvoiceFileStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
