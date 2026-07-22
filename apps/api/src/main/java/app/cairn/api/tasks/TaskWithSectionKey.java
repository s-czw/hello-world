package app.cairn.api.tasks;

/** A task plus its section's sort key ("" for the no-section group), used for grouped list ordering. */
public record TaskWithSectionKey(Task task, String sectionSortKey) {}
