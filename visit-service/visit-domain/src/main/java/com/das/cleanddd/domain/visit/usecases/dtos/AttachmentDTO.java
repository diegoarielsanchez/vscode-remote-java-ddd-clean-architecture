package com.das.cleanddd.domain.visit.usecases.dtos;

import java.io.InputStream;

/**
 * Carries a single attachment from the HTTP layer to the use case.
 *
 * <p>Binary content is represented as an {@link InputStream} so that the bytes
 * are never fully materialised in heap memory as a {@code byte[]}.  The
 * storage port reads the stream exactly once, computes the SHA-256 digest while
 * writing to storage, and returns the resulting metadata value object.</p>
 *
 * @param contentLength byte count reported by the HTTP layer
 *                      ({@code MultipartFile.getSize()}); used for early
 *                      size-limit validation before the stream is consumed.
 */
public record AttachmentDTO(
        String fileName,
        String contentType,
        long   contentLength,
        InputStream content) {}
