package model;

import java.time.LocalDate;

public final class RoadmapMilestone {
    private final int milestoneId;
    private String title;
    private String description;
    private LocalDate dueDate;
    private boolean completed;

    public RoadmapMilestone(int milestoneId, String title, String description,
                            LocalDate dueDate, boolean completed) {
        if (milestoneId < 1) throw new IllegalArgumentException("Milestone ID must be positive.");
        this.milestoneId = milestoneId;
        update(title, description, dueDate);
        this.completed = completed;
    }

    public int getMilestoneId() { return milestoneId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getDueDate() { return dueDate; }
    public boolean isCompleted() { return completed; }

    public void update(String title, String description, LocalDate dueDate) {
        String validTitle = requireText(title, "Milestone title");
        String validDescription = requireText(description, "Milestone description");
        LocalDate validDueDate = requireDate(dueDate);
        this.title = validTitle;
        this.description = validDescription;
        this.dueDate = validDueDate;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }

    private static LocalDate requireDate(LocalDate value) {
        if (value == null) throw new IllegalArgumentException("Milestone due date is required.");
        return value;
    }
}
