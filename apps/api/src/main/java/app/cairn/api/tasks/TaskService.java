package app.cairn.api.tasks;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.core.error.ApiException;
import app.cairn.api.core.error.ForbiddenException;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.projects.ProjectDeletingEvent;
import app.cairn.api.projects.ProjectService;
import app.cairn.api.projects.SectionService;
import app.cairn.api.tasks.ordering.Ordering;
import app.cairn.api.tasks.web.UpdateTaskRequest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tasks domain service: CRUD, completion, and fractional-order moves.
 *
 * <p>The server always computes the {@code sort_key}: create appends to the end of its section; move
 * derives a key strictly between the target neighbours via {@link Ordering}. Reads order by
 * {@code (sort_key, created_at, id)}, so even if two concurrent moves land equal keys the list is still
 * a stable total order with no lost or duplicated rows. When a key grows past
 * {@link Ordering#MAX_KEY_LENGTH} the section is rebalanced to short keys.
 */
@Service
public class TaskService {

    private static final Set<String> PRIORITIES = Set.of("none", "low", "medium", "high");

    private final TaskRepository tasks;
    private final ProjectService projects;
    private final SectionService sections;
    private final MembershipService memberships;

    public TaskService(
            TaskRepository tasks,
            ProjectService projects,
            SectionService sections,
            MembershipService memberships) {
        this.tasks = tasks;
        this.projects = projects;
        this.sections = sections;
        this.memberships = memberships;
    }

    public List<TaskWithSectionKey> listByProject(UUID projectId, TaskCursor after, int limit) {
        projects.requireProject(projectId);
        return tasks.pageByProject(projectId, after, limit);
    }

    public Task get(UUID id) {
        return tasks.findById(id).orElseThrow(() -> NotFoundException.of("Task"));
    }

    public List<Task> myOpenTasks(UUID userId) {
        return tasks.myOpenTasks(userId);
    }

    @Transactional
    public Task create(
            AuthPrincipal caller,
            UUID projectId,
            UUID sectionId,
            UUID assigneeId,
            String title,
            String description,
            String priority,
            LocalDate dueDate) {
        projects.requireProject(projectId);
        if (sectionId != null) {
            sections.requireSectionInProject(sectionId, projectId);
        }
        if (assigneeId != null) {
            requireMember(assigneeId);
        }
        String prio = priority == null ? "none" : priority;
        requireValidPriority(prio);
        String sortKey = Ordering.after(tasks.maxSortKey(projectId, sectionId, null).orElse(null));
        UUID id = tasks.insert(
                projectId, sectionId, assigneeId, title.trim(), trimToNull(description), prio, dueDate,
                caller.userId(), sortKey);
        return get(id);
    }

    @Transactional
    public Task update(UUID id, UpdateTaskRequest req) {
        Task task = get(id);
        if (req.titlePresent() && (req.getTitle() == null || req.getTitle().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Task title cannot be blank");
        }
        if (req.assigneePresent() && req.getAssigneeId() != null) {
            requireMember(req.getAssigneeId());
        }
        if (req.priorityPresent() && req.getPriority() != null) {
            requireValidPriority(req.getPriority());
        }

        boolean completedSet = req.completedPresent();
        boolean completedAtSet = false;
        Boolean completedVal = null;
        OffsetDateTime completedAtVal = null;
        if (completedSet) {
            boolean nowCompleted = Boolean.TRUE.equals(req.getCompleted());
            completedVal = nowCompleted;
            completedAtSet = true;
            completedAtVal = nowCompleted
                    ? (task.completedAt() != null ? task.completedAt() : OffsetDateTime.now())
                    : null;
        }

        TaskUpdate u = new TaskUpdate(
                req.titlePresent(), req.getTitle() == null ? null : req.getTitle().trim(),
                req.descriptionPresent(), trimToNull(req.getDescription()),
                req.assigneePresent(), req.getAssigneeId(),
                req.duePresent(), req.getDueDate(),
                req.priorityPresent(), req.getPriority(),
                completedSet, completedVal,
                completedAtSet, completedAtVal);
        tasks.update(id, u);
        return get(id);
    }

    @Transactional
    public void delete(AuthPrincipal caller, UUID id) {
        Task task = get(id);
        boolean allowed = caller.isAdmin()
                || caller.userId().equals(task.createdBy())
                || caller.userId().equals(task.assigneeId())
                || callerOwnsProject(caller, task.projectId());
        if (!allowed) {
            throw new ForbiddenException(
                    "Only an admin, the task creator/assignee, or the project owner may delete this task");
        }
        tasks.delete(id);
    }

    /**
     * Move a task within/into a section, placing it between the given anchors (each optional). The
     * server computes the fractional key; anchors not present in the target section are ignored.
     */
    @Transactional
    public Task move(UUID taskId, UUID targetSectionId, UUID beforeTaskId, UUID afterTaskId) {
        Task task = get(taskId);
        UUID projectId = task.projectId();
        if (targetSectionId != null) {
            sections.requireSectionInProject(targetSectionId, projectId);
        }

        String afterKey = null;
        String beforeKey = null;
        boolean haveAfter = false;
        boolean haveBefore = false;
        if (afterTaskId != null) {
            var k = tasks.sortKeyIfInSection(afterTaskId, projectId, targetSectionId);
            if (k.isPresent()) {
                afterKey = k.get();
                haveAfter = true;
            }
        }
        if (beforeTaskId != null) {
            var k = tasks.sortKeyIfInSection(beforeTaskId, projectId, targetSectionId);
            if (k.isPresent()) {
                beforeKey = k.get();
                haveBefore = true;
            }
        }
        if (haveAfter && !haveBefore) {
            beforeKey = tasks.successorKey(projectId, targetSectionId, afterKey, taskId).orElse(null);
        } else if (haveBefore && !haveAfter) {
            afterKey = tasks.predecessorKey(projectId, targetSectionId, beforeKey, taskId).orElse(null);
        } else if (!haveAfter && !haveBefore) {
            afterKey = tasks.maxSortKey(projectId, targetSectionId, taskId).orElse(null); // append
        }

        String newKey = Ordering.between(afterKey, beforeKey);
        if (Ordering.needsRebalance(newKey)) {
            rebalanceAndPlace(taskId, projectId, targetSectionId, newKey);
        } else {
            tasks.moveTo(taskId, targetSectionId, newKey);
        }
        return get(taskId);
    }

    /** Reassign fresh short keys to a section (rare), inserting the moved task at its computed slot. */
    private void rebalanceAndPlace(UUID taskId, UUID projectId, UUID targetSectionId, String newKey) {
        List<TaskKey> existing = tasks.orderedIdKeysInSection(projectId, targetSectionId, taskId);
        int insertAt = 0;
        while (insertAt < existing.size() && existing.get(insertAt).sortKey().compareTo(newKey) < 0) {
            insertAt++;
        }
        List<UUID> ids = new ArrayList<>(existing.stream().map(TaskKey::id).toList());
        ids.add(insertAt, taskId);
        List<String> keys = Ordering.rebalance(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equals(taskId)) {
                tasks.moveTo(taskId, targetSectionId, keys.get(i));
            } else {
                tasks.assignKeys(List.of(ids.get(i)), List.of(keys.get(i)));
            }
        }
    }

    // --- used by the projects module -----------------------------------------

    /** True if a section still holds tasks (the section-delete guard). */
    public boolean sectionHasTasks(UUID sectionId) {
        return tasks.sectionHasTasks(sectionId);
    }

    /** Relocate every task from one section to another, appending in current order. */
    @Transactional
    public void reassignSection(UUID fromSectionId, UUID toSectionId) {
        List<UUID> ids = tasks.orderedTaskIdsForSection(fromSectionId);
        String last = tasks.maxSortKeyForSection(toSectionId).orElse(null);
        for (UUID id : ids) {
            String key = Ordering.after(last);
            tasks.moveTo(id, toSectionId, key);
            last = key;
        }
    }

    /** Delete a project's tasks when the project is being deleted (synchronous, same transaction). */
    @EventListener
    public void onProjectDeleting(ProjectDeletingEvent event) {
        tasks.deleteByProject(event.projectId());
    }

    // --- helpers -------------------------------------------------------------

    private boolean callerOwnsProject(AuthPrincipal caller, UUID projectId) {
        return projects.get(projectId).ownerId() != null
                && projects.get(projectId).ownerId().equals(caller.userId());
    }

    private void requireMember(UUID userId) {
        if (memberships.findByUserId(userId).isEmpty()) {
            throw new NotFoundException("User " + userId + " is not a member of this organization");
        }
    }

    private static void requireValidPriority(String priority) {
        if (!PRIORITIES.contains(priority)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "priority must be none|low|medium|high");
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
