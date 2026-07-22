package app.cairn.api.projects;

import java.util.Set;

/** The project status enum (D-009): a project-level status, never a task-level one. */
public final class ProjectStatus {

    public static final String ON_TRACK = "on_track";
    public static final String AT_RISK = "at_risk";
    public static final String OFF_TRACK = "off_track";
    public static final String ON_HOLD = "on_hold";

    public static final Set<String> VALUES = Set.of(ON_TRACK, AT_RISK, OFF_TRACK, ON_HOLD);

    private ProjectStatus() {}

    public static boolean isValid(String status) {
        return status != null && VALUES.contains(status);
    }
}
