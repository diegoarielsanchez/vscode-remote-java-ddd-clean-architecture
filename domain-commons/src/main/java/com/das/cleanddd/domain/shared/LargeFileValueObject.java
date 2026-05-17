package com.das.cleanddd.domain.shared;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/**
 * Value Object base class for large graphical / binary files.
 *
 * <h2>Why this class does NOT extend {@link FileValueObject}</h2>
 * <p>{@link FileValueObject} stores the entire file as a {@code byte[]} and makes
 * a <em>defensive copy</em> at construction, so peak JVM heap usage would be
 * <strong>2 × file size</strong>.  For large graphical assets (high-resolution
 * images, TIFF scans, etc.) this causes unacceptable GC pressure and risks
 * {@code OutOfMemoryError}.  Therefore this class follows a
 * <strong>metadata-only</strong> design: it retains only the file name, content
 * type, size in bytes, and the SHA-256 integrity hash — never the raw bytes.</p>
 *
 * <h2>Usage pattern</h2>
 * <p>The raw {@code byte[]} (or {@code InputStream}) is passed to the static
 * factory {@link #of(String, String, long, byte[])} at the application / upload
 * boundary.  The factory computes the hash and immediately discards the bytes;
 * only the metadata VO travels through the domain model.  The infrastructure
 * layer (e.g. S3, a blob store, or a filesystem service) owns the binary
 * content and can re-verify integrity by comparing against {@link #sha256Hash()}.</p>
 *
 * <h2>On SHA-256</h2>
 * <p>SHA-256 remains the right choice for integrity verification.  The hash is
 * computed once at the application boundary and stored; no re-hashing is needed
 * inside the domain.</p>
 *
 * <p>Subclass example:</p>
 * <pre>{@code
 * public final class PatientXRayImage extends LargeFileValueObject {
 *
 *     private PatientXRayImage(String fileName, String contentType,
 *                              long sizeInBytes, String sha256Hash) {
 *         super(fileName, contentType, sizeInBytes, sha256Hash);
 *     }
 *
 *     public static PatientXRayImage of(String fileName, String contentType,
 *                                       long sizeInBytes, byte[] content) {
 *         LargeFileValueObject base =
 *             LargeFileValueObject.of(fileName, contentType, sizeInBytes, content);
 *         return new PatientXRayImage(base.fileName(), base.contentType(),
 *                                     base.sizeInBytes(), base.sha256Hash());
 *     }
 *
 *     @Override
 *     protected Set<String> allowedMimeTypes() {
 *         return Set.of("image/tiff", "image/jpeg");
 *     }
 *
 *     @Override
 *     protected long maxSizeInBytes() {
 *         return 50L * 1024 * 1024; // 50 MB
 *     }
 * }
 * }</pre>
 */
public abstract class LargeFileValueObject {

    /** Default maximum accepted file size: 100 MB. */
    public static final int  DEFAULT_MAX_SIZE_MB    = 100;
    public static final long DEFAULT_MAX_SIZE_BYTES = (long) DEFAULT_MAX_SIZE_MB * 1024 * 1024;

    /**
     * MIME types accepted by default for graphical files.
     * Subclasses may restrict this set via {@link #allowedMimeTypes()}.
     */
    public static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml",
            "image/tiff",
            "image/bmp",
            "image/x-icon"
    );

    // ── Metadata fields (raw bytes are NOT stored) ────────────────────────

    private final String fileName;
    private final String contentType;
    private final long   sizeInBytes;
    private final String sha256Hash;

    /**
     * Protected constructor used by subclasses and by {@link #of}.
     * Raw bytes are not passed here — pass them to {@link #of} so the hash
     * can be computed before this constructor is called.
     */
    protected LargeFileValueObject(String fileName, String contentType,
                                   long sizeInBytes, String sha256Hash) {
        validateFileName(fileName);
        validateContentType(contentType);
        validateSizeValue(sizeInBytes);
        validateHashPresent(sha256Hash);
        this.fileName    = fileName.trim();
        this.contentType = contentType.trim();
        this.sizeInBytes = sizeInBytes;
        this.sha256Hash  = sha256Hash;
        validateMimeType(this.contentType);
        validateSizeLimit(sizeInBytes);
    }

    // ── Static factory ────────────────────────────────────────────────────

    /**
     * Creates a concrete anonymous instance of {@code LargeFileValueObject}
     * from the supplied raw bytes.  The hash is computed here and the bytes
     * are not retained by the returned object.
     *
     * <p>Subclasses should expose their own {@code of(…)} factory that
     * delegates to this one and wraps the result in the concrete type.</p>
     *
     * @param fileName    original file name; must not be blank.
     * @param contentType MIME type; must be one of {@link #allowedMimeTypes()}.
     * @param sizeInBytes declared file size in bytes (must be &gt; 0 and match
     *                    the actual {@code content} length).
     * @param content     raw file bytes used solely to compute the SHA-256 hash;
     *                    not retained after this call.
     */
    public static LargeFileValueObject of(String fileName, String contentType,
                                          long sizeInBytes, byte[] content) {
        validateContent(content);
        if (content.length != sizeInBytes) {
            throw new IllegalArgumentException(
                    "Declared size " + sizeInBytes
                    + " bytes does not match actual content length " + content.length + " bytes.");
        }
        String hash = computeSha256(content);
        return new LargeFileValueObject(fileName, contentType, sizeInBytes, hash) {};
    }

    /**
     * Creates a metadata-only instance from values that have already been computed
     * externally — typically by a storage port that streamed the bytes through a
     * {@code DigestInputStream} while writing them to disk or a blob store.
     *
     * <p>Use this factory when the raw bytes are never materialised in heap memory:
     * the infrastructure layer computes the SHA-256 hash as it streams the content
     * to storage, then hands back the digest together with the byte count, and the
     * domain model is reconstructed from those values alone.</p>
     *
     * @param fileName    original file name; must not be blank.
     * @param contentType MIME type; must not be blank.
     * @param sizeInBytes actual number of bytes written; must be &gt; 0.
     * @param sha256Hash  lowercase hex SHA-256 digest; must not be blank.
     * @return a validated {@code LargeFileValueObject} holding only metadata.
     */
    public static LargeFileValueObject ofMetadata(String fileName, String contentType,
                                                   long sizeInBytes, String sha256Hash) {
        return new LargeFileValueObject(fileName, contentType, sizeInBytes, sha256Hash) {};
    }

    // ── Queries ───────────────────────────────────────────────────────────

    /** Returns the original file name (e.g. {@code "photo.jpg"}). */
    public String fileName()    { return fileName; }

    /** Returns the MIME content type (e.g. {@code "image/jpeg"}). */
    public String contentType() { return contentType; }

    /** Returns the file size in bytes. */
    public long sizeInBytes()   { return sizeInBytes; }

    /**
     * Returns the lowercase hex-encoded SHA-256 digest computed at upload time.
     * Store this alongside the file reference and re-verify after loading binary
     * content from storage to detect corruption or tampering.
     */
    public String sha256Hash()  { return sha256Hash; }

    // ── Extension points ──────────────────────────────────────────────────

    /**
     * Returns the set of permitted MIME types.
     * Override to restrict to a narrower set (e.g. only {@code "image/png"}).
     */
    protected Set<String> allowedMimeTypes() {
        return ALLOWED_MIME_TYPES;
    }

    /**
     * Returns the maximum allowed file size in bytes.
     * Override to lower (or carefully raise) this limit for a specific context.
     */
    protected long maxSizeInBytes() {
        return DEFAULT_MAX_SIZE_BYTES;
    }

    // ── Validation ────────────────────────────────────────────────────────

    private static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is required.");
        }
    }

    private static void validateContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type is required.");
        }
    }

    private static void validateSizeValue(long sizeInBytes) {
        if (sizeInBytes <= 0) {
            throw new IllegalArgumentException("File size must be greater than zero.");
        }
    }

    private static void validateHashPresent(String sha256Hash) {
        if (sha256Hash == null || sha256Hash.isBlank()) {
            throw new IllegalArgumentException("SHA-256 hash is required.");
        }
    }

    private static void validateContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("File content must not be empty.");
        }
    }

    private void validateMimeType(String contentType) {
        String normalized = contentType.toLowerCase();
        if (!allowedMimeTypes().contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported content type '" + contentType + "'. "
                    + "Allowed types: " + allowedMimeTypes());
        }
    }

    private void validateSizeLimit(long sizeInBytes) {
        if (sizeInBytes > maxSizeInBytes()) {
            throw new IllegalArgumentException(
                    "File size " + sizeInBytes + " bytes exceeds the maximum allowed "
                    + maxSizeInBytes() + " bytes ("
                    + (maxSizeInBytes() / (1024 * 1024)) + " MB).");
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
        if (!(o instanceof LargeFileValueObject other)) return false;
        return sizeInBytes == other.sizeInBytes
                && Objects.equals(fileName, other.fileName)
                && Objects.equals(contentType, other.contentType)
                && Objects.equals(sha256Hash, other.sha256Hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, contentType, sizeInBytes, sha256Hash);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{fileName='" + fileName + "', contentType='" + contentType
                + "', sizeInBytes=" + sizeInBytes
                + ", sha256='" + sha256Hash + "'}";
    }
}
