package com.das.cleanddd.domain.visit.usecases.dtos;

/**
 * Represents the result of storing a single attachment:
 * the original file name plus the storage reference (e.g. S3 key, filesystem path).
 */
public record AttachmentResultDTO(String fileName, String storageReference, String sha256Hash) {}
