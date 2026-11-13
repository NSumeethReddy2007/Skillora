import model.Session;
import model.Skill;
import model.Student;
import service.SessionManager;
import service.StudentManager;
import storage.FileManager;

import java.time.LocalDate;
import java.time.LocalTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free checks for the Module 8 business rules; run with assertions enabled. */
public final class SessionManagerChecks {
    public static void main(String[] args) {
        StudentManager students = new StudentManager();
        Student teacher = new Student("T1", "Teacher", "teacher@campus.edu", "CS", 2);
        teacher.addSkillToTeach(new Skill("Java", "Technology"));
        Student learner = new Student("L1", "Learner", "learner@campus.edu", "Arts", 1);
        learner.addSkillToLearn(new Skill("Java", "Technology"));
        students.registerStudent(teacher);
        students.registerStudent(learner);
        SessionManager manager = new SessionManager(students);
        LocalDate future = LocalDate.now().plusDays(2);
        Session session = manager.createSession("T1", "L1", "Java", future,
                LocalTime.of(10, 30), 60);
        assert session.getSessionId() == 1;
        assert manager.getSession(1).getSkill().getName().equals("Java");
        expectFailure(() -> manager.createSession("T1", "missing", "Java", future,
                LocalTime.NOON, 60));
        expectFailure(() -> manager.createSession("T1", "T1", "Java", future,
                LocalTime.NOON, 60));
        expectFailure(() -> manager.createSession("T1", "L1", "Unknown", future,
                LocalTime.NOON, 60));
        expectFailure(() -> manager.createSession("T1", "L1", " ", future,
                LocalTime.NOON, 60));
        expectFailure(() -> manager.createSession("T1", "L1", "Java", null,
                LocalTime.NOON, 60));
        expectFailure(() -> manager.createSession("T1", "L1", "Java", future,
                LocalTime.NOON, 0));
        expectFailure(() -> manager.createSession("T1", "L1", "Java", LocalDate.now().minusDays(1),
                LocalTime.NOON, 60));
        manager.cancelSession(1, "L1");
        assert manager.getSession(1).getStatus() == Session.Status.CANCELLED;
        expectFailure(() -> manager.completeSession(1, "L1"));
        expectFailure(() -> manager.cancelSession(1, "someone-else"));
        Session second = manager.createSession("T1", "L1", "Java", future,
                LocalTime.of(13, 0), 45);
        assert manager.hasSessionsForStudent("T1");
        assert manager.hasSessionsForStudent("l1");
        manager.updateSession(second.getSessionId(), "T1", future, LocalTime.of(14, 0), 50);
        manager.completeSession(second.getSessionId(), "L1");
        assert manager.getSession(second.getSessionId()).getStatus() == Session.Status.COMPLETED;
        expectFailure(() -> manager.cancelSession(second.getSessionId(), "L1"));
        expectFailure(() -> manager.getSession(second.getSessionId()).updateSchedule(
                future, LocalTime.of(15, 0), 30));
        Session third = manager.createSession("T1", "L1", "Java", future,
                LocalTime.of(16, 0), 45);
        manager.updateSession(third.getSessionId(), "T1", future.plusDays(1),
                LocalTime.of(15, 30), 55);
        manager.cancelSession(third.getSessionId(), "L1");
        try {
            Path directory = Files.createTempDirectory("skillora-session-check");
            FileManager files = new FileManager(directory);
            files.saveAllData(students.getAllStudents(), List.of(), manager.getAllSessions(),
                    List.of(), List.of());
            FileManager.AllData loaded = files.loadAllData();
            StudentManager reloadedStudents = new StudentManager();
            reloadedStudents.loadStudents(loaded.students());
            SessionManager reloadedSessions = new SessionManager(reloadedStudents);
            for (FileManager.SavedSession saved : loaded.sessions()) {
                reloadedSessions.restoreSession(saved.sessionId(), saved.teacherId(), saved.learnerId(),
                        saved.skillName(), saved.date(), saved.startTime(), saved.durationMinutes(), saved.status());
            }
            assert reloadedSessions.getSession(1).getStatus() == Session.Status.CANCELLED;
            assert reloadedSessions.getSession(1).getDate().equals(future);
            assert reloadedSessions.getSession(second.getSessionId()).getStatus() == Session.Status.COMPLETED;
            assert reloadedSessions.getSession(second.getSessionId()).getStartTime().equals(LocalTime.of(14, 0));
            assert reloadedSessions.getSession(third.getSessionId()).getStatus() == Session.Status.CANCELLED;
            assert reloadedSessions.getSession(third.getSessionId()).getDate().equals(future.plusDays(1));
            assert reloadedSessions.getSession(third.getSessionId()).getDurationMinutes() == 55;
            assert loaded.students().size() == 2;
        } catch (java.io.IOException exception) {
            throw new AssertionError("Persistence check failed.", exception);
        }
        try {
            Path directory = Files.createTempDirectory("skillora-invalid-save-check");
            Files.writeString(directory.resolve("students.txt"), "keep-students");
            Files.writeString(directory.resolve("requests.txt"), "keep-requests");
            FileManager files = new FileManager(directory);
            expectFailure(() -> {
                try { files.saveData(List.of(teacher, learner), List.of(), null); }
                catch (java.io.IOException exception) { throw new AssertionError(exception); }
            });
            assert Files.readString(directory.resolve("students.txt")).equals("keep-students");
            assert Files.readString(directory.resolve("requests.txt")).equals("keep-requests");
        } catch (java.io.IOException exception) {
            throw new AssertionError("Invalid-save protection check failed.", exception);
        }
        System.out.println("SessionManager checks passed.");
    }

    private static void expectFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected operation to fail.");
    }
}
