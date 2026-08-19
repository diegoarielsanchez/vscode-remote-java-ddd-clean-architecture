package com.das.infra.service.settlement;

import com.das.cleanddd.domain.settlement.entities.IInvoiceFileStorage.FileIntegrityException;
import com.das.cleanddd.domain.settlement.entities.InvoiceFile;
import com.das.cleanddd.domain.settlement.entities.InvoiceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LocalDiskInvoiceFileStorage}.
 *
 * Uses JUnit 5's {@code @TempDir} so every test runs against an isolated
 * temporary directory that is deleted automatically after the test.
 */
class LocalDiskInvoiceFileStorageTest {

    @TempDir
    Path tempDir;

    private LocalDiskInvoiceFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDiskInvoiceFileStorage(tempDir.toString());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(bytes));
    }

    private InvoiceId randomInvoiceId() {
        return new InvoiceId(UUID.randomUUID().toString());
    }

    // ── store ──────────────────────────────────────────────────────────────────

    @Test
    void store_writesFileToExpectedPath() throws Exception {
        InvoiceId invoiceId = randomInvoiceId();
        byte[]    content   = "invoice content".getBytes();
        InvoiceFile file    = new InvoiceFile("invoice.pdf", "application/pdf", content);

        storage.store(invoiceId, file);

        Path expected = tempDir.resolve(invoiceId.value()).resolve("invoice.pdf");
        assertThat(expected).exists();
        assertThat(Files.readAllBytes(expected)).isEqualTo(content);
    }

    @Test
    void store_createsIntermediateDirectories_whenNotPresent() {
        InvoiceId   invoiceId = randomInvoiceId();
        InvoiceFile file      = new InvoiceFile("doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        storage.store(invoiceId, file);

        assertThat(tempDir.resolve(invoiceId.value())).isDirectory();
    }

    @Test
    void store_overwritesExistingFile_whenStoredTwice() throws Exception {
        InvoiceId   invoiceId = randomInvoiceId();
        InvoiceFile first     = new InvoiceFile("doc.pdf", "application/pdf", "first".getBytes());
        InvoiceFile second    = new InvoiceFile("doc.pdf", "application/pdf", "second".getBytes());

        storage.store(invoiceId, first);
        storage.store(invoiceId, second);

        Path stored = tempDir.resolve(invoiceId.value()).resolve("doc.pdf");
        assertThat(new String(Files.readAllBytes(stored))).isEqualTo("second");
    }

    @Test
    void store_sanitizesPathTraversalInFileName() throws Exception {
        InvoiceId   invoiceId = randomInvoiceId();
        byte[]      content   = "data".getBytes();
        // Attempt path traversal — only the last component should be used
        InvoiceFile file      = new InvoiceFile("../../etc/passwd", "text/plain", content);

        storage.store(invoiceId, file);

        Path safe = tempDir.resolve(invoiceId.value()).resolve("passwd");
        assertThat(safe).exists();
        // The traversal target must NOT exist
        assertThat(tempDir.resolve("etc").resolve("passwd")).doesNotExist();
    }

    // ── loadContent ───────────────────────────────────────────────────────────

    @Test
    void loadContent_returnsStoredBytes_whenHashMatches() throws Exception {
        InvoiceId   invoiceId = randomInvoiceId();
        byte[]      content   = "pdf bytes".getBytes();
        InvoiceFile file      = new InvoiceFile("report.pdf", "application/pdf", content);
        storage.store(invoiceId, file);

        String hash   = sha256Hex(content);
        Optional<byte[]> loaded = storage.loadContent(invoiceId, "report.pdf", hash);

        assertThat(loaded).isPresent();
        assertThat(loaded.get()).isEqualTo(content);
    }

    @Test
    void loadContent_returnsEmpty_whenFileDoesNotExist() throws Exception {
        InvoiceId invoiceId = randomInvoiceId();

        Optional<byte[]> result = storage.loadContent(invoiceId, "missing.pdf", "anyhash");

        assertThat(result).isEmpty();
    }

    @Test
    void loadContent_throwsFileIntegrityException_whenHashMismatches() throws Exception {
        InvoiceId   invoiceId = randomInvoiceId();
        byte[]      content   = "real content".getBytes();
        InvoiceFile file      = new InvoiceFile("doc.pdf", "application/pdf", content);
        storage.store(invoiceId, file);

        assertThatThrownBy(() ->
                storage.loadContent(invoiceId, "doc.pdf", "deadbeef00000000"))
                .isInstanceOf(FileIntegrityException.class)
                .hasMessageContaining(invoiceId.value());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesStoredFile() throws Exception {
        InvoiceId   invoiceId = randomInvoiceId();
        InvoiceFile file      = new InvoiceFile("old.pdf", "application/pdf", "data".getBytes());
        storage.store(invoiceId, file);

        storage.delete(invoiceId, "old.pdf");

        assertThat(tempDir.resolve(invoiceId.value()).resolve("old.pdf")).doesNotExist();
    }

    @Test
    void delete_doesNotThrow_whenFileDoesNotExist() {
        // Should silently succeed (idempotent)
        storage.delete(randomInvoiceId(), "nonexistent.pdf");
    }
}
