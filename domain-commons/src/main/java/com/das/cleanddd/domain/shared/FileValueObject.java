package com.das.cleanddd.domain.shared;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Generic Value Object base class for a binary file.
 *
 * <p>Encapsulates the minimal, domain-agnostic rules that every file must satisfy
 * regardless of the bounded context it belongs to:</p>
 * <ul>
 *   <li>{@code fileName} must not be blank.</li>
 *   <li>{@code contentType} must not be blank.</li>
 *   <li>{@code content} must not be null or empty.</li>
 * </ul>
 *
 * <p>A SHA-256 digest of the raw bytes is computed <em>eagerly</em> at construction
 * time and exposed via {@link #sha256Hash()}.  It is stored alongside the file
 * metadata (e.g. in a relational database) so that integrity can be re-verified
 * after the binary content is loaded back from storage.</p>
 *
 * <p>Subclasses are expected to add bounded-context-specific business rules
 * (e.g. allowed MIME types, maximum file size, file-extension requirements).
 * Follow the same pattern used by {@link StringValueObject} → {@link EmailValueObject}.</p>
 *
 * <p>The class is immutable: {@link #content()} always returns a defensive copy of
 * the internal byte array.</p>
 */
public abstract class FileValueObject {

    private final String fileName;
    private final String contentType;
    private final byte[] content;
    private final String sha256Hash;

    /**
     * @param fileName    original file name; must not be blank.
     * @param contentType MIME type of the file; must not be blank.
     * @param content     raw file bytes; must not be null or empty.
     */
    protected FileValueObject(String fileName, String contentType, byte[] content) {
        validateFileName(fileName);
        validateContentType(contentType);
        validateContent(content);
        this.fileName    = fileName.trim();
        this.contentType = contentType.trim();
        this.content     = Arrays.copyOf(content, content.length);
        this.sha256Hash  = computeSha256(this.content);
    }

    // ── Queries ────────────────────────────────────────────────────────────

    /** Returns the original file name (e.g. {@code "report.pdf"}). */
    public String fileName() {
        return fileName;
    }

    /** Returns the MIME content type (e.g. {@code "application/pdf"}). */
    public String contentType() {
        return contentType;
    }

    /** Returns a defensive copy of the raw file bytes. */
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    /** Returns the size of the file in bytes. */
    public long sizeInBytes() {
        return content.length;
    }

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of the file content,
     * computed at construction time.
     *
     * <p>Store this value alongside the file metadata and re-verify it after
     * loading the binary content from storage to detect corruption or tampering.</p>
     */
    public String sha256Hash() {
        return sha256Hash;
    }

    // ── Generic validation ────────────────────────────────────────────────

    private static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is required.");
        }
    }

    private static void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("File content type is required.");
        }
    }

    private static void validateContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("File content must not be empty.");
        }
    }

    // ── Hash computation ──────────────────────────────────────────────────

    private static String computeSha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java spec — this can never happen.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    // ── Object identity ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FileValueObject other)) return false;
        return Objects.equals(fileName, other.fileName)
                && Objects.equals(contentType, other.contentType)
                && Arrays.equals(content, other.content);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(fileName, contentType);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{fileName='" + fileName + "', contentType='" + contentType
                + "', sizeInBytes=" + content.length
                + ", sha256='" + sha256Hash + "'}";
    }
}
