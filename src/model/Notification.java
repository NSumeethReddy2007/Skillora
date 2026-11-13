package model;

import java.time.Instant;

/** An in-app message addressed to one registered student. */
public final class Notification {
    private final int notificationId;
    private String recipientId;
    private final String eventType;
    private final String message;
    private final Instant createdAt;
    private boolean read;

    public Notification(int notificationId, String recipientId, String eventType,
                        String message, Instant createdAt, boolean read) {
        if (notificationId < 1) throw new IllegalArgumentException("Notification ID must be positive.");
        this.notificationId = notificationId;
        this.recipientId = requireText(recipientId, "Recipient student ID");
        this.eventType = requireText(eventType, "Notification type");
        this.message = requireText(message, "Notification message");
        if (createdAt == null) throw new IllegalArgumentException("Notification timestamp is required.");
        this.createdAt = createdAt;
        this.read = read;
    }

    public int getNotificationId() { return notificationId; }
    public String getRecipientId() { return recipientId; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
    public void markAsRead() { read = true; }
    public void reassignRecipient(String recipientId) {
        this.recipientId = requireText(recipientId, "Recipient student ID");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }
}
