package service;

import model.Notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/** In-memory notification operations; FileManager provides persistence. */
public final class NotificationManager {
    private final StudentManager studentManager;
    private final ArrayList<Notification> notifications = new ArrayList<>();
    private int nextNotificationId = 1;

    public NotificationManager(StudentManager studentManager) {
        if (studentManager == null) throw new IllegalArgumentException("Student manager is required.");
        this.studentManager = studentManager;
    }

    public Notification createNotification(String recipientId, String eventType, String message) {
        String validId = requireRecipient(recipientId);
        Notification notification = new Notification(nextNotificationId, validId,
                eventType, message, Instant.now(), false);
        notifications.add(notification);
        nextNotificationId++;
        return notification;
    }

    public Notification createNotificationIfAbsent(String recipientId, String eventType, String message) {
        String validId = requireRecipient(recipientId);
        return notifications.stream()
                .filter(item -> item.getRecipientId().equalsIgnoreCase(validId)
                        && item.getEventType().equals(eventType)
                        && item.getMessage().equals(message))
                .findFirst()
                .orElseGet(() -> createNotification(validId, eventType, message));
    }

    public List<Notification> getNotificationsForStudent(String studentId) {
        String validId = requireRecipient(studentId);
        return notifications.stream()
                .filter(item -> item.getRecipientId().equalsIgnoreCase(validId))
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed()
                        .thenComparing(Comparator.comparingInt(Notification::getNotificationId).reversed()))
                .toList();
    }

    public Notification markAsRead(int notificationId, String studentId) {
        String validId = requireRecipient(studentId);
        Notification notification = notifications.stream()
                .filter(item -> item.getNotificationId() == notificationId)
                .findFirst().orElseThrow(() -> new NoSuchElementException("Notification was not found."));
        if (!notification.getRecipientId().equalsIgnoreCase(validId))
            throw new IllegalArgumentException("Only the notification recipient can mark it as read.");
        notification.markAsRead();
        return notification;
    }

    public int getUnreadCount(String studentId) {
        return (int) getNotificationsForStudent(studentId).stream().filter(item -> !item.isRead()).count();
    }

    public int deleteNotificationsForStudent(String studentId) {
        String validId = requireRecipient(studentId);
        int previousSize = notifications.size();
        notifications.removeIf(item -> item.getRecipientId().equalsIgnoreCase(validId));
        return previousSize - notifications.size();
    }

    public void updateStudentId(String oldStudentId, String newStudentId) {
        if (oldStudentId == null || oldStudentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        String oldId = oldStudentId.trim();
        String newId = requireRecipient(newStudentId);
        notifications.stream()
                .filter(item -> item.getRecipientId().equalsIgnoreCase(oldId))
                .forEach(item -> item.reassignRecipient(newId));
    }

    public void discardNotification(int notificationId) {
        notifications.removeIf(item -> item.getNotificationId() == notificationId);
    }

    public List<Notification> getAllNotifications() { return List.copyOf(notifications); }

    public void restoreNotification(int notificationId, String recipientId, String eventType,
                                   String message, Instant createdAt, boolean read) {
        if (notifications.stream().anyMatch(item -> item.getNotificationId() == notificationId))
            throw new IllegalArgumentException("Notification data contains a duplicate ID.");
        notifications.add(new Notification(notificationId, requireRecipient(recipientId),
                eventType, message, createdAt, read));
        nextNotificationId = Math.max(nextNotificationId, notificationId + 1);
    }

    private String requireRecipient(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Recipient student ID is required.");
        return studentManager.findStudentById(studentId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Recipient student ID was not found."))
                .getId();
    }
}
