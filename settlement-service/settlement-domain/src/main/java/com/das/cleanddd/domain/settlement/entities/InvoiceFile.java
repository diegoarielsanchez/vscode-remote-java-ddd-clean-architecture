package com.das.cleanddd.domain.settlement.entities;

import com.das.cleanddd.domain.shared.FileValueObject;

/**
 * Value Object representing the digital version of an invoice file.
 *
 * <p>Extends the generic {@link FileValueObject} with settlement-specific rules:</p>
 * <ul>
 *   <li>File name must include a file extension (e.g. {@code "INV-001.pdf"}).</li>
 *   <li>Content type must follow MIME format (e.g. {@code "application/pdf"}).</li>
 *   <li>File size must not exceed {@value #MAX_SIZE_BYTES} bytes (10 MB).</li>
 * </ul>
 */
public final class InvoiceFile extends FileValueObject {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    /**
     * Creates a new {@code InvoiceFile}.
     *
     * @param fileName    the original file name, including extension (e.g. {@code "INV-001.pdf"}).
     * @param contentType the MIME type of the file (e.g. {@code "application/pdf"}).
     * @param content     the raw file bytes; must not exceed 10 MB.
     */
    public InvoiceFile(String fileName, String contentType, byte[] content) {
        super(fileName, contentType, content);
        validateInvoiceFileName(fileName);
        validateInvoiceContentType(contentType);
        validateInvoiceContent(content);
    }

    // ── Settlement-specific validation ────────────────────────────────────

    private static void validateInvoiceFileName(String fileName) {
        if (!fileName.trim().contains(".")) {
            throw new IllegalArgumentException(
                    "Invoice file name must include a file extension (e.g. 'invoice.pdf').");
        }
    }

    private static void validateInvoiceContentType(String contentType) {
        if (!contentType.trim().contains("/")) {
            throw new IllegalArgumentException(
                    "Invoice file content type must be a valid MIME type (e.g. 'application/pdf').");
        }
    }

    private static void validateInvoiceContent(byte[] content) {
        if (content.length > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Invoice file must not exceed " + (MAX_SIZE_BYTES / (1024 * 1024)) + " MB.");
        }
    }
}
