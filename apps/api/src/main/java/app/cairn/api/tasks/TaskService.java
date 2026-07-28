package app.cairn.api.tasks;

import app.cairn.api.auth.AuthPrincipal;
import app.cairn.api.core.error.ApiException;
import app.cairn.api.core.error.ConflictException;
import app.cairn.api.core.error.ForbiddenException;
import app.cairn.api.core.error.NotFoundException;
import app.cairn.api.orgs.member.MembershipService;
import app.cairn.api.projects.ProjectDeletingEvent;
import app.cairn.api.projects.ProjectService;
import app.cairn.api.projects.SectionService;
import app.cairn.api.tasks.event.TaskEvents;
import app.cairn.api.tasks.ordering.Ordering;
import app.cairn.api.tasks.web.UpdateTaskRequest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tasks domain service: CRUD, completion, fractional-order moves, subtasks (one level), and promote.
 *
 * <p>The server always computes the {@code sort_key}: create appends to the end of its section; move
 * derives a key strictly between the target neighbours via {@link Ordering}. Reads order by
 * {@code (sort_key, created_at, id)}, so even if two concurrent moves land equal keys the list is still
 * a stable total order with no lost or duplicated rows. When a key grows past
 * {@link Ordering#MAX_KEY_LENGTH} the section is rebalanced to short keys.
 *
 * <p>State transitions publish {@link TaskEvents} within the transaction. The {@code activity} module
 * records them; the {@code notifications} module (next phase) fans out. Deleting a task publishes
 * {@link TaskEvents.TaskDeleting} first so comments/attachments/activity are cleaned before the FK-owning
 * row is removed.
 */
@Service
public class TaskService {

    private static final Set<String> PRIORITIES = Set.of("none", "low", "medium", "high");

    private final TaskRepository tasks;
    private final ProjectService projects;
    private final SectionService sections;
    private final MembershipService memberships;
    private final ApplicationEventPublisher events;

    public TaskService(
            TaskRepository tasks,
            ProjectService projects,
            SectionService sections,
            MembershipService memberships,
            ApplicationEventPublisher events) {
        this.tasks = tasks;
        this.projects = projects;
        this.sections = sections;
        this.memberships = memberships;
        this.events = events;
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

    /** Subtasks of a task, in order. */
    public List<Task> subtasks(UUID parentId) {
        return tasks.subtasksByParent(parentId);
    }

    /** {total, completed} subtask counts for a task. */
    public int[] subtaskProgress(UUID parentId) {
        return tasks.subtaskProgress(parentId);
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
        Task created = get(id);
        events.publishEvent(new TaskEvents.TaskCreated(
                id, created.projectId(), caller.userId(), created.assigneeId(), created.title(), false));
        return created;
    }

    /** Add a subtask under a parent. One level only: a subtask cannot itself have subtasks (409). */
    @Transactional
    public Task createSubtask(AuthPrincipal caller, UUID parentId, String title) {
        Task parent = get(parentId);
        if (parent.isSubtask()) {
            throw new ConflictException("Cannot add a subtask to a subtask (subtasks are one level deep)");
        }
        String key = Ordering.after(tasks.maxSubtaskSortKey(parentId).orElse(null));
        UUID id = tasks.insertSubtask(parent.projectId(), parentId, null, title.trim(), caller.userId(), key);
        Task created = get(id);
        events.publishEvent(new TaskEvents.TaskCreated(
                id, created.projectId(), caller.userId(), created.assigneeId(), created.title(), true));
        return created;
    }

    /** Promote a subtask to a full task: clear its parent and append it to the given section (or none). */
    @Transactional
    public Task promote(AuthPrincipal caller, UUID id, UUID sectionId) {
        Task task = get(id);
        if (!task.isSubtask()) {
            throw new ConflictException("Task is not a subtask");
        }
        if (sectionId != null) {
            sections.requireSectionInProject(sectionId, task.projectId());
        }
        String sortKey = Ordering.after(tasks.maxSortKey(task.projectId(), sectionId, id).orElse(null));
        tasks.promote(id, sectionId, sortKey);
        Task promoted = get(id);
        events.publishEvent(new TaskEvents.TaskSectionChanged(
                id, promoted.projectId(), caller.userId(), null, sectionId, promoted.title()));
        return promoted;
    }

    @Transactional
    public Task update(AuthPrincipal caller, UUID id, UpdateTaskRequest req) {
        Task before = get(id);
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
                    ? (before.completedAt() != null ? before.completedAt() : OffsetDateTime.now())
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
        Task after = get(id);

        publishFieldEvents(caller, before, after);
        return after;
    }

    /** Emit the field-change domain events (assignee / due / completion) after an update. */
    private void publishFieldEvents(AuthPrincipal caller, Task before, Task after) {
        UUID actor = caller.userId();
        if (!Objects.equals(before.assigneeId(), after.assigneeId())) {
            events.publishEvent(new TaskEvents.TaskAssigneeChanged(
                    after.id(), after.projectId(), actor, before.assigneeId(), after.assigneeId(), after.title()));
        }
        if (!Objects.equals(before.dueDate(), after.dueDate())) {
            events.publishEvent(new TaskEvents.TaskDueChanged(
                    after.id(), after.projectId(), actor, after.assigneeId(),
                    before.dueDate(), after.dueDate(), after.title()));
        }
        if (before.completed() != after.completed()) {
            events.publishEvent(new TaskEvents.TaskCompletionChanged(
                    after.id(), after.projectId(), actor, after.assigneeId(), after.title(), after.completed()));
        }
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
        cascadeDelete(id);
    }

    /**
     * Move a task within/into a section, placing it between the given anchors (each optional). The
     * server computes the fractional key; anchors not present in the target section are ignored.
     */
    @Transactional
    public Task move(AuthPrincipal caller, UUID taskId, UUID targetSectionId, UUID beforeTaskId, UUID afterTaskId) {
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
        if (!Objects.equals(task.sectionId(), targetSectionId)) {
            events.publishEvent(new TaskEvents.TaskSectionChanged(
                    taskId, projectId, caller.userId(), task.sectionId(), targetSectionId, task.title()));
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
        for (UUID id : tasks.topLevelIdsInProject(event.projectId())) {
            cascadeDelete(id);
        }
    }

    /**
     * Delete a task, its subtasks, and everything hanging off them. Each task publishes a
     * {@link TaskEvents.TaskDeleting} before its row is removed so comments/attachments/activity are
     * cleaned first (children before the parent, honouring the self-referential FK).
     */
    private void cascadeDelete(UUID taskId) {
        for (UUID childId : tasks.subtaskIds(taskId)) {
            events.publishEvent(new TaskEvents.TaskDeleting(childId));
            tasks.delete(childId);
        }
        events.publishEvent(new TaskEvents.TaskDeleting(taskId));
        tasks.delete(taskId);
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
