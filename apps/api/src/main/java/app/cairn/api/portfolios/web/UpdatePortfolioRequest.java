package app.cairn.api.portfolios.web;

import java.util.UUID;

/**
 * PATCH body for a portfolio. A mutable bean (not a record) so Jackson calls each setter only when the
 * JSON key is present, distinguishing "omitted → unchanged" from "null → clear" for nullable columns.
 */
public class UpdatePortfolioRequest {

    private String name;
    private boolean namePresent;
    private String description;
    private boolean descriptionPresent;
    private String color;
    private boolean colorPresent;
    private UUID ownerId;
    private boolean ownerPresent;

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
}
