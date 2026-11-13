package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class GroupLearningSession {
    public enum Status { SCHEDULED, COMPLETED, CANCELLED }

    private final int groupSessionId;
    private final int groupId;
    private String organizerId;
    private String title;
    private String description;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private int durationMinutes;
    private Status status;

    public GroupLearningSession(int groupSessionId, int groupId, String title, String description,
                                LocalDate scheduledDate, LocalTime scheduledTime, int durationMinutes,
                                String organizerId) {
        if (groupSessionId < 1 || groupId < 1)
            throw new IllegalArgumentException("Group session and group IDs must be positive.");
        this.groupSessionId = groupSessionId;
        this.groupId = groupId;
        this.organizerId = requireText(organizerId, "Organizer student ID");
        this.status = Status.SCHEDULED;
        update(title, description, scheduledDate, scheduledTime, durationMinutes);
    }

    public int getGroupSessionId() { return groupSessionId; }
    public int getGroupId() { return groupId; }
    public String getOrganizerId() { return organizerId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getScheduledDate() { return scheduledDate; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public Status getStatus() { return status; }

    public void update(String title, String description, LocalDate date, LocalTime time, int durationMinutes) {
        if (status != Status.SCHEDULED)
            throw new IllegalStateException("Only scheduled group sessions can be rescheduled.");
        String validTitle = requireText(title, "Group session title");
        String validDescription = requireText(description, "Group session description");
        if (date == null || time == null)
            throw new IllegalArgumentException("Group session date and time are required.");
        if (durationMinutes < 1 || durationMinutes > 1440)
            throw new IllegalArgumentException("Group session duration must be between 1 and 1440 minutes.");
        this.title = validTitle;
        this.description = validDescription;
        this.scheduledDate = date;
        this.scheduledTime = time;
        this.durationMinutes = durationMinutes;
    }

    public void setStatus(Status status) {
        if (this.status != Status.SCHEDULED)
            throw new IllegalStateException("Only scheduled group sessions can change status.");
        if (status != Status.COMPLETED && status != Status.CANCELLED)
            throw new IllegalArgumentException("A scheduled group session can only be completed or cancelled.");
        this.status = status;
    }

    public void reassignOrganizer(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Organizer student ID is required.");
        organizerId = studentId.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }
}
