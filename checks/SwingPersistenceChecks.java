import model.ExchangeRequest;
import model.Session;
import model.Skill;
import model.Student;
import service.ExchangeRequestManager;
import service.NotificationManager;
import service.PortfolioManager;
import service.RatingManager;
import service.SessionManager;
import service.StudentManager;
import storage.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Persistence checks for the five record types loaded and saved by the Swing application. */
public final class SwingPersistenceChecks {
    private static final String TEACHER_ID = "SWING-PERSIST-TEACHER";
    private static final String LEARNER_ID = "SWING-PERSIST-LEARNER";
    private static final String UPDATED_LEARNER_ID = "SWING-PERSIST-LEARNER-UPDATED";
    private static final String[] DATA_FILES = {
            "students.txt", "requests.txt", "sessions.txt", "ratings.txt", "notifications.txt"
    };

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Files.createTempDirectory("skillora-swing-persistence-");
        try {
            verifyAllRecordsSurviveRestart(dataDirectory);
            verifyFailedSaveRollsBackAllFiles(dataDirectory);
            System.out.println("Swing persistence checks passed for all five record types and rollback.");
        } finally {
            deleteTemporaryDirectory(dataDirectory);
        }
    }

    private static void verifyAllRecordsSurviveRestart(Path dataDirectory) throws Exception {
        FileManager files = new FileManager(dataDirectory);
        Managers managers = createInitialManagers();
        files.saveAllData(managers.students().getAllStudents(), managers.exchanges().getAllRequests(),
                managers.sessions().getAllSessions(), managers.ratings().getAllRatings(),
                managers.notifications().getAllNotifications());

        Managers restored = restoreManagers(files.loadAllData());
        require(restored.students().getAllStudents().size() == 2, "Student records did not survive restart.");
        require(restored.exchanges().getAllRequests().size() == 1
                        && restored.exchanges().getAllRequests().getFirst().getStatus()
                        == ExchangeRequest.Status.ACCEPTED,
                "Exchange request did not survive restart.");
        Session session = restored.sessions().getSession(1);
        require(session.getStatus() == Session.Status.COMPLETED
                        && session.getTeacher().getId().equals(TEACHER_ID)
                        && session.getLearner().getId().equals(LEARNER_ID),
                "Session did not survive restart with its participants and status.");
        require(restored.ratings().getRatingsForSession(1).size() == 1
                        && restored.ratings().getRatingsForSession(1).getFirst()
                        .getSubject().getId().equals(LEARNER_ID),
                "Rating did not survive restart with its reviewed student.");
        require(restored.notifications().getNotificationsForStudent(LEARNER_ID).size() == 1
                        && restored.notifications().getUnreadCount(LEARNER_ID) == 1,
                "Notification did not survive restart with its unread state.");

        Student existingLearner = restored.students().findStudentById(LEARNER_ID).orElseThrow();
        Student updatedLearner = new Student(UPDATED_LEARNER_ID, existingLearner.getName(),
                existingLearner.getEmail(), existingLearner.getDepartment(), existingLearner.getYearOfStudy());
        existingLearner.getSkillsToTeach().forEach(updatedLearner::addSkillToTeach);
        existingLearner.getSkillsToLearn().forEach(updatedLearner::addSkillToLearn);
        restored.students().updateStudent(LEARNER_ID, updatedLearner);
        restored.notifications().updateStudentId(LEARNER_ID, UPDATED_LEARNER_ID);
        require(session.getLearner() == existingLearner
                        && restored.ratings().getRatingsForSession(1).getFirst().getSubject() == existingLearner
                        && existingLearner.getId().equals(UPDATED_LEARNER_ID),
                "Student ID update did not preserve session and rating object references.");

        files.saveAllData(restored.students().getAllStudents(), restored.exchanges().getAllRequests(),
                restored.sessions().getAllSessions(), restored.ratings().getAllRatings(),
                restored.notifications().getAllNotifications());
        Managers afterIdUpdate = restoreManagers(files.loadAllData());
        require(afterIdUpdate.students().findStudentById(UPDATED_LEARNER_ID).isPresent()
                        && afterIdUpdate.sessions().getSession(1).getLearner().getId().equals(UPDATED_LEARNER_ID)
                        && afterIdUpdate.ratings().getRatingsForSession(1).getFirst()
                        .getSubject().getId().equals(UPDATED_LEARNER_ID)
                        && afterIdUpdate.notifications().getNotificationsForStudent(UPDATED_LEARNER_ID).size() == 1
                        && afterIdUpdate.exchanges().getAllRequests().getFirst().getRecipient().getId()
                        .equals(UPDATED_LEARNER_ID),
                "Student ID update did not persist across all referencing records.");
    }

    private static void verifyFailedSaveRollsBackAllFiles(Path dataDirectory) throws Exception {
        Map<String, byte[]> before = snapshotFiles(dataDirectory);
        AtomicBoolean failAtRatings = new AtomicBoolean(true);
        FileManager failingFiles = new FileManager(dataDirectory, stage -> {
            if (stage == FileManager.PersistenceStage.RATINGS && failAtRatings.compareAndSet(true, false))
                throw new IOException("Injected rating-file write failure.");
        });
        Managers managers = restoreManagers(failingFiles.loadAllData());
        try {
            failingFiles.saveAllData(managers.students().getAllStudents(), managers.exchanges().getAllRequests(),
                    managers.sessions().getAllSessions(), managers.ratings().getAllRatings(),
                    managers.notifications().getAllNotifications());
            throw new AssertionError("Expected coordinated persistence to report a write failure.");
        } catch (IOException expected) {
            require(expected.getMessage().contains("Injected rating-file write failure."),
                    "Persistence failure did not preserve the underlying cause.");
        }
        Map<String, byte[]> after = snapshotFiles(dataDirectory);
        for (String fileName : DATA_FILES) {
            require(java.util.Arrays.equals(before.get(fileName), after.get(fileName)),
                    fileName + " changed despite a failed coordinated save.");
        }
    }

    private static Managers createInitialManagers() {
        StudentManager students = new StudentManager();
        Student teacher = new Student(TEACHER_ID, "Teacher", "teacher-swing@example.edu", "Computing", 2);
        teacher.addSkillToTeach(new Skill("Java", "Technology"));
        teacher.addSkillToLearn(new Skill("Art", "Creative"));
        Student learner = new Student(LEARNER_ID, "Learner", "learner-swing@example.edu", "Arts", 1);
        learner.addSkillToTeach(new Skill("Art", "Creative"));
        learner.addSkillToLearn(new Skill("Java", "Technology"));
        students.registerStudent(teacher);
        students.registerStudent(learner);

        ExchangeRequestManager exchanges = new ExchangeRequestManager(students);
        ExchangeRequest request = exchanges.createRequest(TEACHER_ID, LEARNER_ID, "Java", "Art");
        exchanges.acceptRequest(request.getRequestId(), LEARNER_ID);

        SessionManager sessions = new SessionManager(students);
        Session session = sessions.createSession(TEACHER_ID, LEARNER_ID, "Java",
                LocalDate.now().plusDays(2), LocalTime.of(10, 30), 60);
        sessions.completeSession(session.getSessionId(), LEARNER_ID);

        RatingManager ratings = new RatingManager(students, sessions);
        ratings.submitRating(session.getSessionId(), TEACHER_ID, 5, "Clear and helpful lesson.");
        NotificationManager notifications = new NotificationManager(students);
        notifications.createNotification(LEARNER_ID, "SESSION_COMPLETED", "The session is complete.");
        PortfolioManager portfolios = new PortfolioManager(students, sessions, ratings);
        require(portfolios.getPortfolio(LEARNER_ID).ratingCount() == 1,
                "Test portfolio did not include its received rating.");
        return new Managers(students, exchanges, sessions, notifications, ratings);
    }

    private static Managers restoreManagers(FileManager.AllData data) {
        StudentManager students = new StudentManager();
        students.loadStudents(data.students());
        ExchangeRequestManager exchanges = new ExchangeRequestManager(students);
        data.requests().forEach(saved -> exchanges.restoreRequest(saved.requestId(), saved.requesterId(),
                saved.recipientId(), saved.offeredSkillName(), saved.requestedSkillName(), saved.status()));
        SessionManager sessions = new SessionManager(students);
        data.sessions().forEach(saved -> sessions.restoreSession(saved.sessionId(), saved.teacherId(),
                saved.learnerId(), saved.skillName(), saved.date(), saved.startTime(),
                saved.durationMinutes(), saved.status()));
        RatingManager ratings = new RatingManager(students, sessions);
        data.ratings().forEach(saved -> ratings.restoreRating(saved.sessionId(), saved.reviewerId(),
                saved.score(), saved.feedback(), saved.createdAt()));
        NotificationManager notifications = new NotificationManager(students);
        data.notifications().forEach(saved -> notifications.restoreNotification(saved.notificationId(),
                saved.recipientId(), saved.eventType(), saved.message(), saved.createdAt(), saved.read()));
        return new Managers(students, exchanges, sessions, notifications, ratings);
    }

    private static Map<String, byte[]> snapshotFiles(Path dataDirectory) throws IOException {
        java.util.Map<String, byte[]> snapshots = new java.util.LinkedHashMap<>();
        for (String fileName : DATA_FILES)
            snapshots.put(fileName, Files.readAllBytes(dataDirectory.resolve(fileName)));
        return snapshots;
    }

    private static void deleteTemporaryDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Managers(StudentManager students, ExchangeRequestManager exchanges,
                            SessionManager sessions, NotificationManager notifications,
                            RatingManager ratings) { }
}
