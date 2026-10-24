package com.das.cleanddd.domain.catalog.entities;

import java.time.LocalDate;

/**
 * What identifies the physical medicine behind a product for distribution: batch (lot) number, serial number
 * and expiration date. Each is optional ({@code null} means not on file).
 */
public record ProductTraceability(ProductBatchNumber batchNumber, ProductSerialNumber serialNumber,
                                  ProductExpirationDate expirationDate) {

    public static final ProductTraceability NONE = new ProductTraceability(null, null, null);

    /** Builds it from raw values: {@code null} means absent, anything else must be valid. */
    public static ProductTraceability of(String batchNumber, String serialNumber, LocalDate expirationDate) {
        return new ProductTraceability(
                batchNumber == null ? null : new ProductBatchNumber(batchNumber),
                serialNumber == null ? null : new ProductSerialNumber(serialNumber),
                expirationDate == null ? null : new ProductExpirationDate(expirationDate));
    }

    /**
     * The result of an update. For each field {@code null} keeps the current value, so a client that does not
     * send it never erases it. An empty batch or serial number ({@code ""}) removes it. An expiration date can be
     * replaced but not removed: a medicine that had one keeps one.
     */
    public ProductTraceability updatedWith(String newBatchNumber, String newSerialNumber, LocalDate newExpirationDate) {
        return new ProductTraceability(
                newBatchNumber == null ? batchNumber
                        : newBatchNumber.isEmpty() ? null : new ProductBatchNumber(newBatchNumber),
                newSerialNumber == null ? serialNumber
                        : newSerialNumber.isEmpty() ? null : new ProductSerialNumber(newSerialNumber),
                newExpirationDate == null ? expirationDate : new ProductExpirationDate(newExpirationDate));
    }

    public String batchNumberValue() {
        return batchNumber == null ? null : batchNumber.value();
    }

    public String serialNumberValue() {
        return serialNumber == null ? null : serialNumber.value();
    }

    public LocalDate expirationDateValue() {
        return expirationDate == null ? null : expirationDate.value();
    }
}
