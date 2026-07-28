package app.cairn.api.attachments;

import java.io.IOException;
import org.springframework.core.io.Resource;

/**
 * Where attachment bytes live. M3 ships a single local-disk implementation ({@link LocalDiskStorage});
 * S3/object storage is a commercialization-phase concern (arch §10) and is intentionally not built here.
 * Keys are opaque, server-generated paths ({@code orgId/attachmentId/filename}).
 */
public interface StorageProvider {

    /** Persist the given bytes at {@code storageKey}, creating any parent structure. */
    void store(String storageKey, byte[] content) throws IOException;

    /** A readable resource for {@code storageKey}. The caller checks {@code exists()}. */
    Resource load(String storageKey);

    /** Remove the bytes at {@code storageKey}. Returns true if something was deleted. */
    boolean delete(String storageKey);
}
