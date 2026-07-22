package app.cairn.api.projects;

import java.util.UUID;

/** Lightweight project identity (id + name + color) for embedding in other modules' rows. */
public record ProjectMeta(UUID id, String name, String color) {}
