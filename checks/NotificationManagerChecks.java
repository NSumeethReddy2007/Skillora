import model.Notification;
import model.Student;
import service.NotificationManager;
import service.StudentManager;
import storage.FileManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/** Focused checks for notification validation, recipient scope, unread counts, and persistence. */
public final class NotificationManagerChecks {
    public static void main(String[] args) throws Exception {
        StudentManager students = new StudentManager();
        students.registerStudent(new Student("N1", "First Student", "first@example.edu", "CS", 1));
        students.registerStudent(new Student("N2", "Second Student", "second@example.edu", "Arts", 2));
        NotificationManager manager = new NotificationManager(students);

        expectFailure(() -> manager.createNotification("missing", "TEST", "Message"));
        expectFailure(() -> manager.createNotification("N1", "TEST", "  "));
        Notification first = manager.createNotification("n1", "TEST", "A saved update");
        Notification duplicate = manager.createNotificationIfAbsent("N1", "TEST", "A saved update");
        assert duplicate.getNotificationId() == first.getNotificationId();
        Notification distinctEvent = manager.createNotificationIfAbsent(
                "N1", "SESSION_RESCHEDULED", "Session #3 changed schedule.");
        assert distinctEvent.getNotificationId() != first.getNotificationId();
        manager.createNotification("N2", "TEST", "Other student's update");
        assert manager.getNotificationsForStudent("N1").size() == 2;
        assert manager.getUnreadCount("N1") == 2;
        expectFailure(() -> manager.markAsRead(first.getNotificationId(), "N2"));
        manager.markAsRead(first.getNotificationId(), "N1");
        assert manager.getUnreadCount("N1") == 1;

        Path directory = Files.createTempDirectory("skillora-notification-manager-check");
        FileManager files = new FileManager(directory);
        files.saveNotifications(manager.getAllNotifications());
        List<FileManager.SavedNotification> saved = files.loadNotifications();
        assert saved.size() == 3;
        assert saved.stream().anyMatch(item -> item.recipientId().equals("N1") && item.read());

        NotificationManager reloaded = new NotificationManager(students);
        for (FileManager.SavedNotification item : saved) {
            reloaded.restoreNotification(item.notificationId(), item.recipientId(), item.eventType(),
                    item.message(), item.createdAt(), item.read());
        }
        assert reloaded.getUnreadCount("N1") == 1;
        assert reloaded.getUnreadCount("N2") == 1;
        students.updateStudent("N1", new Student("N1-RENAMED", "First Student",
                "first@example.edu", "CS", 1));
        manager.updateStudentId("N1", "N1-RENAMED");
        assert manager.getUnreadCount("N1-RENAMED") == 1;
        expectFailure(() -> manager.getNotificationsForStudent("N1"));
        expectFailure(() -> reloaded.getNotificationsForStudent(" "));
        expectFailure(() -> new Notification(1, "N1", "TEST", " ", Instant.now(), false));
        System.out.println("NotificationManager checks passed.");
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected validation to fail.");
    }
}
