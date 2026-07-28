package app.cairn.api.projects.web;

import app.cairn.api.projects.Section;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A section as returned to clients. */
public record SectionResponse(
        UUID id, UUID projectId, String name, String sortKey, OffsetDateTime createdAt) {

    public static SectionResponse from(Section s) {
        return new SectionResponse(s.id(), s.projectId(), s.name(), s.sortKey(), s.createdAt());
    }
}
