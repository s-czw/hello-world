package app.cairn.api.activity;

import app.cairn.api.tasks.event.TaskEvents;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Writes the {@code activity_log} from task domain events (C3). Listeners run synchronously inside the
 * publishing transaction (same request, org seam live). The {@code notifications} module attaches its own
 * listeners to the same events in the next phase — this service only records the audit trail.
 */
@Service
public class ActivityService {

    private final ActivityRepository activity;
    private final ObjectMapper mapper;

    public ActivityService(ActivityRepository activity, ObjectMapper mapper) {
        this.activity = activity;
        this.mapper = mapper;
    }

    public List<ActivityEntry> listByTask(UUID taskId) {
        return activity.listByTask(taskId);
    }

    @EventListener
    void onCreated(TaskEvents.TaskCreated e) {
        activity.insert(e.actorId(), "task.created", ActivityRepository.RESOURCE_TASK, e.taskId(), null);
    }

    @EventListener
    void onCompletion(TaskEvents.TaskCompletionChanged e) {
        String action = e.completed() ? "task.completed" : "task.reopened";
        activity.insert(e.actorId(), action, ActivityRepository.RESOURCE_TASK, e.taskId(), null);
    }

    @EventListener
    void onAssignee(TaskEvents.TaskAssigneeChanged e) {
        activity.insert(
                e.actorId(), "task.assignee_changed", ActivityRepository.RESOURCE_TASK, e.taskId(),
                fromTo(str(e.oldAssigneeId()), str(e.newAssigneeId())));
    }

    @EventListener
    void onDue(TaskEvents.TaskDueChanged e) {
        activity.insert(
                e.actorId(), "task.due_changed", ActivityRepository.RESOURCE_TASK, e.taskId(),
                fromTo(str(e.oldDue()), str(e.newDue())));
    }

    @EventListener
    void onSection(TaskEvents.TaskSectionChanged e) {
        activity.insert(
                e.actorId(), "task.section_changed", ActivityRepository.RESOURCE_TASK, e.taskId(),
                fromTo(str(e.oldSectionId()), str(e.newSectionId())));
    }

    /** Drop a task's activity rows when the task is being deleted (sync, same tx). */
    @EventListener
    void onTaskDeleting(TaskEvents.TaskDeleting e) {
        activity.deleteByTask(e.taskId());
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private String fromTo(String from, String to) {
        ObjectNode node = mapper.createObjectNode();
        if (from == null) {
            node.putNull("from");
        } else {
            node.put("from", from);
        }
        if (to == null) {
            node.putNull("to");
        } else {
            node.put("to", to);
        }
        return node.toString();
    }
}
