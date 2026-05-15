package com.das.inframySQL.service.visit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.LargeFileValueObject;
import com.das.cleanddd.domain.visit.ports.IProductPromoAttachmentStorage;

/**
 * Stores product-promo attachments on the local filesystem under a
 * configurable root directory.  Replace with an S3/blob-store
 * implementation by swapping this bean out.
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
    public String store(String visitId, LargeFileValueObject metadata, byte[] content) {
        try {
            Path visitDir = storageRoot.resolve(visitId);
            Files.createDirectories(visitDir);

            // Sanitise file name to prevent path traversal
            String safeName = Paths.get(metadata.fileName()).getFileName().toString();
            Path target = visitDir.resolve(safeName);

            Files.write(target, content);
            return target.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store attachment: " + metadata.fileName(), e);
        }
    }
}
