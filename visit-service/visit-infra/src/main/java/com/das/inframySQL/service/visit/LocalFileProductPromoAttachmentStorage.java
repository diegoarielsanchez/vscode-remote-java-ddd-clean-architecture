package com.das.inframySQL.service.visit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.LargeFileValueObject;
import com.das.cleanddd.domain.visit.ports.IProductPromoAttachmentStorage;

/**
 * Stores product-promo attachments on the local filesystem under a
 * configurable root directory.  Replace with an S3/blob-store
 * implementation by swapping this bean out.
 *
 * <p>The raw bytes are never materialised as a {@code byte[]} in heap memory.
 * The {@link InputStream} is streamed directly to disk through a
 * {@link DigestInputStream} so that the SHA-256 hash is computed in a single
 * pass while writing, keeping memory usage constant regardless of file size.</p>
 *
 * Storage layout:
 *   {storage.attachments.root}/{visitId}/{fileName}
 */
@Service
public class LocalFileProductPromoAttachmentStorage implements IProductPromoAttachmentStorage {

    private final Path storageRoot;

    public LocalFileProductPromoAttachmentStorage(
            @Value("${storage.attachments.root:./attachments}") String rootPath) {
        this.storageRoot = Paths.get(rootPath).toAbsolutePath().normalize();
    }

    @Override
    public LargeFileValueObject store(String visitId, String fileName, String contentType,
                                      long contentLength, InputStream content) {
        try {
            Path visitDir = storageRoot.resolve(visitId);
            Files.createDirectories(visitDir);

            // Sanitise file name to prevent path traversal
            String safeName = Paths.get(fileName).getFileName().toString();
            Path target = visitDir.resolve(safeName);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytesWritten;
            try (DigestInputStream dis = new DigestInputStream(content, digest)) {
                bytesWritten = Files.copy(dis, target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            String sha256Hash = HexFormat.of().formatHex(digest.digest());
            return LargeFileValueObject.ofMetadata(fileName, contentType, bytesWritten, sha256Hash);

        } catch (IOException e) {
            throw new RuntimeException("Failed to store attachment: " + fileName, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
