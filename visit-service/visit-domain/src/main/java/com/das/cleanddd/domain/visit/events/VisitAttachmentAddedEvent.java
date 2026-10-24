package com.das.cleanddd.domain.visit.events;

/**
 * A file was attached to a visit. {@code sha256} is the hash of the bytes as stored, so the file can later be shown
 * to be the original.
 */
public record VisitAttachmentAddedEvent(
        String visitId,
        String fileName,
        String contentType,
        long sizeInBytes,
        String sha256) implements VisitDomainEvent {

    @Override public String aggregateType() { return VISIT; }
    @Override public String aggregateId() { return visitId; }
    @Override public String eventType() { return "visit.attachment-added"; }
}
