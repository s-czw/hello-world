package app.cairn.api.projects.web;

import java.time.LocalDate;
import java.util.UUID;

/**
 * PATCH body for a project. A mutable bean (not a record) so Jackson calls each setter only when the
 * JSON key is present: this distinguishes "field omitted → leave unchanged" from "field sent as null →
 * clear it", which a plain record/Optional cannot do with the default Jackson modules.
 */
public class UpdateProjectRequest {

    private String name;
    private boolean namePresent;
    private String description;
    private boolean descriptionPresent;
    private String color;
    private boolean colorPresent;
    private String defaultView;
    private boolean defaultViewPresent;
    private Boolean archived;
    private boolean archivedPresent;
    private UUID ownerId;
    private boolean ownerPresent;
    private UUID teamId;
    private boolean teamPresent;
    private LocalDate startDate;
    private boolean startDatePresent;
    private LocalDate endDate;
    private boolean endDatePresent;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    public boolean namePresent() {
        return namePresent;
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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
        this.colorPresent = true;
    }

    public boolean colorPresent() {
        return colorPresent;
    }

    public String getDefaultView() {
        return defaultView;
    }

    public void setDefaultView(String defaultView) {
        this.defaultView = defaultView;
        this.defaultViewPresent = true;
    }

    public boolean defaultViewPresent() {
        return defaultViewPresent;
    }

    public Boolean getArchived() {
        return archived;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
        this.archivedPresent = true;
    }

    public boolean archivedPresent() {
        return archivedPresent;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
        this.ownerPresent = true;
    }

    public boolean ownerPresent() {
        return ownerPresent;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
        this.teamPresent = true;
    }

    public boolean teamPresent() {
        return teamPresent;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
        this.startDatePresent = true;
    }

    public boolean startDatePresent() {
        return startDatePresent;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
        this.endDatePresent = true;
    }

    public boolean endDatePresent() {
        return endDatePresent;
    }
}
