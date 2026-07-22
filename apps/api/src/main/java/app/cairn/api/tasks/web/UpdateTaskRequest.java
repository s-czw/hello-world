package app.cairn.api.tasks.web;

import java.time.LocalDate;
import java.util.UUID;

/**
 * PATCH body for a task. A mutable bean (not a record) so Jackson calls each setter only when the JSON
 * key is present: this distinguishes "omitted → leave unchanged" from "sent as null → clear" (unassign,
 * clear the due date), which the default Jackson modules cannot express with a record/Optional.
 * Setting {@code completed} also stamps/clears {@code completed_at} in the service.
 */
public class UpdateTaskRequest {

    private String title;
    private boolean titlePresent;
    private String description;
    private boolean descriptionPresent;
    private UUID assigneeId;
    private boolean assigneePresent;
    private LocalDate dueDate;
    private boolean duePresent;
    private String priority;
    private boolean priorityPresent;
    private Boolean completed;
    private boolean completedPresent;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        this.titlePresent = true;
    }

    public boolean titlePresent() {
        return titlePresent;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public boolean descriptionPresent() {
        return descriptionPresent;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = assigneeId;
        this.assigneePresent = true;
    }

    public boolean assigneePresent() {
        return assigneePresent;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
        this.duePresent = true;
    }

    public boolean duePresent() {
        return duePresent;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
        this.priorityPresent = true;
    }

    public boolean priorityPresent() {
        return priorityPresent;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
        this.completedPresent = true;
    }

    public boolean completedPresent() {
        return completedPresent;
    }
}
