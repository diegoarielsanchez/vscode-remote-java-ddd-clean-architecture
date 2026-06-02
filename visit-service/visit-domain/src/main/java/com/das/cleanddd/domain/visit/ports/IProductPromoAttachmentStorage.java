package com.das.cleanddd.domain.visit.ports;

import java.io.InputStream;

import com.das.cleanddd.domain.shared.LargeFileValueObject;

/**
 * Port for storing and retrieving product promo attachment binaries.
 * Implementations live in the infrastructure layer (filesystem, S3, etc.).
 */
public interface IProductPromoAttachmentStorage {

    /**
     * Streams the content to storage and returns a metadata value object that
     * includes the storage reference, computed SHA-256 hash, and byte count.
     *
     * <p>The implementation must read {@code content} exactly once, compute the
     * SHA-256 digest while writing (e.g. via {@code DigestInputStream}), and
     * build the returned {@link LargeFileValueObject} from those computed values.
     * The raw bytes are never retained in memory.</p>
     *
     * @param visitId       the ID of the visit this attachment belongs to
     * @param fileName      original file name (used for storage layout)
     * @param contentType   MIME type of the file
     * @param contentLength byte count reported by the caller; may be -1 if unknown
     * @param content       stream of raw bytes — consumed and closed by this method
     * @return a metadata-only {@link LargeFileValueObject} (no {@code byte[]} retained)
     */
    LargeFileValueObject store(String visitId, String fileName, String contentType,
                               long contentLength, InputStream content);
}
