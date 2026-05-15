package com.das.cleanddd.domain.visit.ports;

import com.das.cleanddd.domain.shared.LargeFileValueObject;

/**
 * Port for storing and retrieving product promo attachment binaries.
 * Implementations live in the infrastructure layer (filesystem, S3, etc.).
 */
public interface IProductPromoAttachmentStorage {

    /**
     * Persists the raw bytes and returns a storage reference (e.g. a path or object key)
     * that can later be used to retrieve or delete the file.
     *
     * @param visitId   the ID of the visit this attachment belongs to
     * @param metadata  value object carrying file name, content type, size and SHA-256 hash
     * @param content   raw file bytes (not retained after this call returns)
     * @return an opaque storage reference string
     */
    String store(String visitId, LargeFileValueObject metadata, byte[] content);
}
