package app.cairn.api.attachments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Local-disk {@link StorageProvider} (M3). Root comes from {@code CAIRN_STORAGE_DIR} (property
 * {@code cairn.storage.dir}); dev default is the repo-local {@code .data/attachments} (gitignored),
 * prod default {@code /data/attachments}. Keys are server-generated {@code orgId/attachmentId/filename}
 * paths; every resolve is confined under the root (defence-in-depth against path traversal).
 */
@Component
public class LocalDiskStorage implements StorageProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskStorage.class);

    private final Path root;

    public LocalDiskStorage(
            @Value("${cairn.storage.dir:/home/user/hello-world/.data/attachments}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    private Path resolve(String storageKey) {
        Path p = root.resolve(storageKey).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Storage key escapes the storage root");
        }
        return p;
    }

    @Override
    public void store(String storageKey, byte[] content) throws IOException {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    @Override
    public Resource load(String storageKey) {
        return new FileSystemResource(resolve(storageKey));
    }

    @Override
    public boolean delete(String storageKey) {
        try {
            Path target = resolve(storageKey);
            boolean removed = Files.deleteIfExists(target);
            // best-effort cleanup of the now-empty per-attachment directory
            Path parent = target.getParent();
            if (parent != null && !parent.equals(root) && Files.isDirectory(parent)) {
                try (var s = Files.list(parent)) {
                    if (s.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
            return removed;
        } catch (IOException e) {
            log.warn("Failed to delete attachment bytes for key {}: {}", storageKey, e.getMessage());
            return false;
        }
    }
}
