package com.das.cleanddd.domain.settlement.entities;

import java.util.Optional;

/**
 * Output port for storing and retrieving invoice digital files.
 *
 * <p>The domain defines this abstraction; the infrastructure layer provides
 * the concrete implementation (e.g. local disk, cloud object storage).
 * File metadata is persisted separately in the relational database by
 * {@code ISettlementRepository}.</p>
 */
public interface IInvoiceFileStorage {

    /**
     * Stores the file associated with the given invoice.
     * If a file already exists for that invoice and file name it is overwritten.
     *
     * @param invoiceId the invoice the file belongs to; must not be {@code null}.
     * @param file      the file to store; must not be {@code null}.
     */
    void store(InvoiceId invoiceId, InvoiceFile file);

    /**
     * Loads the raw bytes of a previously stored file and verifies their integrity
     * against the supplied SHA-256 digest.
     *
     * @param invoiceId    the invoice the file belongs to.
     * @param fileName     the original file name used when the file was stored.
     * @param expectedHash the lowercase hex-encoded SHA-256 digest that was persisted
     *                     at store time (obtained via {@code InvoiceFile#sha256Hash()}).
     * @return an {@link Optional} containing the verified file bytes, or empty if not found.
     * @throws FileIntegrityException if the file is found but its hash does not match
     *                                {@code expectedHash}.
     */
    Optional<byte[]> loadContent(InvoiceId invoiceId, String fileName, String expectedHash);

    /**
     * Deletes the stored file.  No-op if the file does not exist.
     *
     * @param invoiceId the invoice the file belongs to.
     * @param fileName  the original file name.
     */
    void delete(InvoiceId invoiceId, String fileName);

    /** Thrown when a loaded file's SHA-256 digest does not match the stored value. */
    class FileIntegrityException extends RuntimeException {
        public FileIntegrityException(String message) {
            super(message);
        }
    }
}
