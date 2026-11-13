import model.GroupLearningSession;
import model.LearningGroup;
import model.Skill;
import model.Student;
import service.GroupLearningManager;
import service.LearningRoadmapManager;
import service.NotificationManager;
import service.RewardManager;
import service.SessionManager;
import service.StudentManager;
import storage.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GroupLearningChecks {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("skillora-group-learning-check-");
        try {
            verifyGroupMembershipAndSessions();
            verifyPersistenceAndRollback(directory);
            System.out.println("Group learning checks passed.");
        } finally {
            deleteTemporaryDirectory(directory);
        }
    }

    private static void verifyGroupMembershipAndSessions() {
        Managers managers = createManagers();
        GroupLearningManager groups = managers.groups();
        LearningGroup group = groups.createGroup("GROUP-A", "Java Circle",
                "Practice Java together.", "Java", 2);
        require(group.getMemberIds().equals(List.of("GROUP-A"))
                        && group.getAvailableSeats() == 1,
                "The creator must be the initial member and count toward capacity.");
        expectArgumentFailure(() -> groups.createGroup("missing", "Invalid", "Description", "Java", 2));
        expectArgumentFailure(() -> groups.createGroup("GROUP-A", " ", "Description", "Java", 2));
        expectArgumentFailure(() -> groups.createGroup("GROUP-A", "Invalid", "Description", "Java", 0));

        groups.joinGroup(group.getGroupId(), "GROUP-B");
        require(group.getMemberIds().size() == 2 && group.getAvailableSeats() == 0,
                "Joining must update group membership and available seats.");
        require(managers.notifications().getNotificationsForStudent("GROUP-A").stream()
                        .filter(notification -> notification.getEventType().equals("GROUP_MEMBER_JOINED")).count() == 1,
                "Joining a group must notify its owner.");
        expectArgumentFailure(() -> groups.joinGroup(group.getGroupId(), "GROUP-B"));
        expectStateFailure(() -> groups.joinGroup(group.getGroupId(), "GROUP-C"));
        expectStateFailure(() -> groups.leaveGroup(group.getGroupId(), "GROUP-A"));
        expectArgumentFailure(() -> groups.leaveGroup(group.getGroupId(), "GROUP-C"));
        expectArgumentFailure(() -> groups.removeMember(group.getGroupId(), "GROUP-B", "GROUP-A"));
        expectArgumentFailure(() -> groups.updateGroup(group.getGroupId(), "GROUP-B",
                "Not owner", "Description", "Java", 3));
        expectArgumentFailure(() -> groups.updateGroup(group.getGroupId(), "GROUP-A",
                "Too small", "Description", "Java", 1));
        groups.updateGroup(group.getGroupId(), "GROUP-A", "Java Learners",
                "Weekly practice.", "Core Java", 3);
        groups.setGroupStatus(group.getGroupId(), "GROUP-A", LearningGroup.Status.CLOSED);
        expectStateFailure(() -> groups.joinGroup(group.getGroupId(), "GROUP-C"));
        groups.setGroupStatus(group.getGroupId(), "GROUP-A", LearningGroup.Status.ACTIVE);

        expectArgumentFailure(() -> groups.scheduleSession(group.getGroupId(), "GROUP-C",
                "Invalid session", "Description", LocalDate.now().plusDays(2), LocalTime.NOON, 60));
        GroupLearningSession scheduled = groups.scheduleSession(group.getGroupId(), "GROUP-B",
                "Collection review", "Review Java collections.", LocalDate.now().plusDays(2),
                LocalTime.of(14, 30), 90);
        expectStateFailure(() -> groups.leaveGroup(group.getGroupId(), "GROUP-B"));
        expectArgumentFailure(() -> groups.rescheduleSession(scheduled.getGroupSessionId(), "GROUP-C",
                "Unauthorized", "Description", LocalDate.now().plusDays(3), LocalTime.NOON, 60));
        LocalDate movedDate = LocalDate.now().plusDays(4);
        groups.rescheduleSession(scheduled.getGroupSessionId(), "GROUP-B", "Collection workshop",
                "Hands-on collections.", movedDate, LocalTime.of(15, 0), 75);
        groups.rescheduleSession(scheduled.getGroupSessionId(), "GROUP-B", "Collection workshop",
                "Hands-on collections.", movedDate, LocalTime.of(15, 0), 75);
        require(scheduled.getScheduledDate().equals(movedDate)
                        && managers.notifications().getNotificationsForStudent("GROUP-A").stream()
                        .filter(notification -> notification.getEventType().equals("GROUP_SESSION_RESCHEDULED"))
                        .count() == 1,
                "Rescheduling must update the session and avoid duplicate notifications.");
        groups.completeSession(scheduled.getGroupSessionId(), "GROUP-B");
        expectStateFailure(() -> groups.cancelSession(scheduled.getGroupSessionId(), "GROUP-A"));

        GroupLearningSession cancelled = groups.scheduleSession(group.getGroupId(), "GROUP-A",
                "Optional practice", "Practice exercises.", LocalDate.now().plusDays(5),
                LocalTime.of(11, 0), 45);
        groups.cancelSession(cancelled.getGroupSessionId(), "GROUP-A");
        expectStateFailure(() -> groups.rescheduleSession(cancelled.getGroupSessionId(), "GROUP-A",
                "Changed", "Description", LocalDate.now().plusDays(6), LocalTime.NOON, 30));
        require(managers.notifications().getNotificationsForStudent("GROUP-B").stream()
                        .anyMatch(notification -> notification.getEventType().equals("GROUP_SESSION_SCHEDULED"))
                        && managers.notifications().getNotificationsForStudent("GROUP-B").stream()
                        .anyMatch(notification -> notification.getEventType().equals("GROUP_SESSION_CANCELLED")),
                "Scheduling and cancelling group sessions must notify members.");

        groups.removeMember(group.getGroupId(), "GROUP-A", "GROUP-B");
        require(!group.hasMember("GROUP-B")
                        && managers.notifications().getNotificationsForStudent("GROUP-B").stream()
                        .anyMatch(notification -> notification.getEventType().equals("GROUP_MEMBER_REMOVED")),
                "Only the owner can remove a member, and the removed student must be notified.");
        groups.joinGroup(group.getGroupId(), "GROUP-B");
        require(managers.notifications().getNotificationsForStudent("GROUP-A").stream()
                        .filter(notification -> notification.getEventType().equals("GROUP_MEMBER_JOINED")).count() == 2,
                "A later rejoin after removal should produce a fresh owner notification.");
        Student oldMember = managers.students().findStudentById("GROUP-B").orElseThrow();
        String oldMemberId = oldMember.getId();
        Student renamedMember = new Student("GROUP-B-RENAMED", oldMember.getName(), oldMember.getEmail(),
                oldMember.getDepartment(), oldMember.getYearOfStudy());
        managers.students().updateStudent(oldMemberId, renamedMember);
        managers.groups().updateStudentId(oldMemberId, renamedMember.getId());
        managers.notifications().updateStudentId(oldMemberId, renamedMember.getId());
        require(group.hasMember(renamedMember.getId())
                        && scheduled.getOrganizerId().equals(renamedMember.getId()),
                "Student ID updates must preserve group membership and organizer references.");
        groups.leaveGroup(group.getGroupId(), renamedMember.getId());
        require(!group.hasMember(renamedMember.getId()), "A regular member must be able to leave their group.");
        require(managers.rewards().getTotalPoints("GROUP-A") == 0
                        && managers.rewards().getTotalPoints("GROUP-B-RENAMED") == 0,
                "Group activities must not award points under the existing reward rules.");
    }

    private static void verifyPersistenceAndRollback(Path directory) throws Exception {
        Managers managers = createManagers();
        LearningGroup group = managers.groups().createGroup("GROUP-A", "Persisted Circle",
                "Saved group description.", "Algorithms", 4);
        managers.groups().joinGroup(group.getGroupId(), "GROUP-B");
        GroupLearningSession session = managers.groups().scheduleSession(group.getGroupId(), "GROUP-B",
                "Graph practice", "Explore graph traversal.", LocalDate.now().plusDays(3),
                LocalTime.of(9, 15), 60);
        managers.groups().completeSession(session.getGroupSessionId(), "GROUP-B");
        managers.groups().setGroupStatus(group.getGroupId(), "GROUP-A", LearningGroup.Status.CLOSED);

        FileManager files = new FileManager(directory);
        saveAll(files, managers);
        byte[] groupFile = Files.readAllBytes(directory.resolve("learning-groups.txt"));
        byte[] sessionFile = Files.readAllBytes(directory.resolve("group-learning-sessions.txt"));
        files.saveAllData(managers.students().getAllStudents(), List.of(), List.of(), List.of(),
                managers.notifications().getAllNotifications(),
                List.of(), List.of(), List.of());
        require(Arrays.equals(groupFile, Files.readAllBytes(directory.resolve("learning-groups.txt")))
                        && Arrays.equals(sessionFile, Files.readAllBytes(
                        directory.resolve("group-learning-sessions.txt"))),
                "The pre-group full-data overload must preserve group files.");

        Managers restored = restoreManagers(files.loadAllData());
        LearningGroup restoredGroup = restored.groups().getGroup(group.getGroupId());
        GroupLearningSession restoredSession = restored.groups().getSession(session.getGroupSessionId());
        require(restoredGroup.getMemberIds().equals(List.of("GROUP-A", "GROUP-B"))
                        && restoredGroup.getStatus() == LearningGroup.Status.CLOSED
                        && restoredSession.getStatus() == GroupLearningSession.Status.COMPLETED
                        && restoredSession.getOrganizerId().equals("GROUP-B"),
                "Group membership, status, and group sessions must survive restart.");
        require(restored.notifications().getAllNotifications().stream()
                        .anyMatch(notification -> notification.getEventType().equals("GROUP_SESSION_SCHEDULED")),
                "Group notifications must persist with the rest of the application data.");

        Map<String, byte[]> originals = List.of("students.txt", "requests.txt", "sessions.txt",
                        "ratings.txt", "notifications.txt", "roadmaps.txt", "rewards.txt",
                        "learning-groups.txt", "group-learning-sessions.txt").stream()
                .collect(java.util.stream.Collectors.toMap(name -> name,
                        name -> readBytes(directory.resolve(name))));
        for (FileManager.PersistenceStage failingStage : List.of(
                FileManager.PersistenceStage.GROUPS, FileManager.PersistenceStage.GROUP_SESSIONS)) {
            AtomicBoolean failOnce = new AtomicBoolean(true);
            FileManager failingFiles = new FileManager(directory, stage -> {
                if (stage == failingStage && failOnce.compareAndSet(true, false))
                    throw new IOException("Injected " + failingStage + " persistence failure.");
            });
            try {
                saveAll(failingFiles, restored);
                throw new AssertionError("Expected coordinated save failure at " + failingStage + ".");
            } catch (IOException expected) {
                require(expected.getMessage().contains("Injected " + failingStage),
                        "The group save failure must be explicit.");
            }
            for (Map.Entry<String, byte[]> original : originals.entrySet()) {
                require(Arrays.equals(original.getValue(), readBytes(directory.resolve(original.getKey()))),
                        "Coordinated rollback must restore " + original.getKey() + ".");
            }
        }
    }

    private static void saveAll(FileManager files, Managers managers) throws IOException {
        files.saveAllData(managers.students().getAllStudents(), List.of(), List.of(), List.of(),
                managers.notifications().getAllNotifications(), List.of(),
                managers.rewards().getAllTransactions(), managers.rewards().getAllBadges(),
                managers.groups().getAllGroups(), managers.groups().getAllSessions());
    }

    private static Managers restoreManagers(FileManager.AllData data) {
        StudentManager students = new StudentManager();
        students.loadStudents(data.students());
        NotificationManager notifications = new NotificationManager(students);
        for (FileManager.SavedNotification saved : data.notifications())
            notifications.restoreNotification(saved.notificationId(), saved.recipientId(), saved.eventType(),
                    saved.message(), saved.createdAt(), saved.read());
        GroupLearningManager groups = new GroupLearningManager(students, notifications);
        for (FileManager.SavedLearningGroup saved : data.groups())
            groups.restoreGroup(saved.groupId(), saved.name(), saved.description(), saved.topic(), saved.ownerId(),
                    saved.creationDate(), saved.maximumMembers(), saved.status(), saved.memberIds());
        for (FileManager.SavedGroupLearningSession saved : data.groupSessions())
            groups.restoreSession(saved.sessionId(), saved.groupId(), saved.title(), saved.description(),
                    saved.date(), saved.time(), saved.durationMinutes(), saved.organizerId(), saved.status());
        return new Managers(students, notifications, groups, new RewardManager(students,
                new SessionManager(students), new LearningRoadmapManager(students)));
    }

    private static Managers createManagers() {
        StudentManager students = new StudentManager();
        Student owner = new Student("GROUP-A", "Avery Owner", "owner@example.test", "Computing", 2);
        Student member = new Student("GROUP-B", "Bailey Member", "member@example.test", "Computing", 2);
        Student outsider = new Student("GROUP-C", "Casey Outsider", "outsider@example.test", "Computing", 1);
        owner.addSkillToTeach(new Skill("Java", "Programming"));
        students.registerStudent(owner);
        students.registerStudent(member);
        students.registerStudent(outsider);
        NotificationManager notifications = new NotificationManager(students);
        GroupLearningManager groups = new GroupLearningManager(students, notifications);
        return new Managers(students, notifications, groups, new RewardManager(students,
                new SessionManager(students), new LearningRoadmapManager(students)));
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + path, exception);
        }
    }

    private static void expectArgumentFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected invalid group operation to be rejected.");
    }

    private static void expectStateFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected invalid group state to be rejected.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTemporaryDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private record Managers(StudentManager students, NotificationManager notifications,
                            GroupLearningManager groups, RewardManager rewards) { }
}
