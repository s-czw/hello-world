package app.cairn.api.attachments;

import org.springframework.core.io.Resource;

/** An attachment's metadata plus a readable resource for its bytes (for streaming a download). */
public record AttachmentDownload(Attachment meta, Resource resource) {}
