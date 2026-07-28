package app.cairn.api.activity.web;

import app.cairn.api.activity.ActivityEntry;
import app.cairn.api.activity.ActivityService;
import app.cairn.api.comments.Comment;
import app.cairn.api.comments.CommentService;
import app.cairn.api.tasks.TaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Merges a task's comments and system activity into one chronological stream (C3). System entries are
 * flagged {@code kind="system"} so the client can collapse them ("Show N updates"). Both source lists are
 * already ordered oldest-first; we concatenate and stably sort by {@code createdAt}.
 */
@Service
public class ActivityStreamService {

    private final TaskService tasks;
    private final CommentService comments;
    private final ActivityService activity;
    private final ObjectMapper mapper;

    public ActivityStreamService(
            TaskService tasks, CommentService comments, ActivityService activity, ObjectMapper mapper) {
        this.tasks = tasks;
        this.comments = comments;
        this.activity = activity;
        this.mapper = mapper;
    }

    public List<ActivityStreamEntry> forTask(UUID taskId) {
        tasks.get(taskId); // 404-for-inaccessible

        List<ActivityStreamEntry> merged = new ArrayList<>();
        for (Comment c : comments.listAll(taskId)) {
            merged.add(new ActivityStreamEntry(
                    ActivityStreamEntry.KIND_COMMENT,
                    c.id().toString(),
                    c.authorId(),
                    null,
                    null,
                    c.body(),
                    c.editedAt() != null,
                    c.editedAt(),
                    c.createdAt()));
        }
        for (ActivityEntry a : activity.listByTask(taskId)) {
            merged.add(new ActivityStreamEntry(
                    ActivityStreamEntry.KIND_SYSTEM,
                    Long.toString(a.id()),
                    a.actorId(),
                    a.action(),
                    parseDiff(a.diffJson()),
                    null,
                    false,
                    null,
                    a.createdAt()));
        }
        merged.sort(Comparator.comparing(ActivityStreamEntry::createdAt));
        return merged;
    }

    private JsonNode parseDiff(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
