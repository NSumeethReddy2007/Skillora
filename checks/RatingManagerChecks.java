import model.Rating;
import model.Session;
import model.Skill;
import model.Student;
import service.PortfolioManager;
import service.RatingManager;
import service.SessionManager;
import service.StudentManager;
import storage.FileManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Focused Module 10 business-rule, portfolio, milestone, and persistence checks. */
public final class RatingManagerChecks {
    public static void main(String[] args) throws Exception {
        StudentManager students = new StudentManager();
        Student teacher = new Student("RATE-T", "Teacher", "teacher-rate@example.edu", "CS", 2);
        teacher.addSkillToTeach(new Skill("Java", "Technology"));
        teacher.addSkillToLearn(new Skill("Drawing", "Creative"));
        Student learner = new Student("RATE-L", "Learner", "learner-rate@example.edu", "Arts", 1);
        learner.addSkillToLearn(new Skill("Java", "Technology"));
        learner.addSkillToTeach(new Skill("Drawing", "Creative"));
        Student outsider = new Student("RATE-X", "Outsider", "outsider-rate@example.edu", "Business", 3);
        students.registerStudent(teacher);
        students.registerStudent(learner);
        students.registerStudent(outsider);

        SessionManager sessions = new SessionManager(students);
        RatingManager ratings = new RatingManager(students, sessions);
        PortfolioManager portfolios = new PortfolioManager(students, sessions, ratings);
        LocalDate baseDate = LocalDate.now().plusDays(5);
        Session first = sessions.createSession("RATE-T", "RATE-L", "Java", baseDate,
                LocalTime.of(10, 0), 60);

        expectStateFailure(() -> ratings.submitRating(first.getSessionId(), teacher.getId(), 5, "Not completed yet"));
        expectArgumentFailure(() -> new Rating(first.getSessionId(), teacher, learner, 0, "Too low", java.time.Instant.now()));
        expectArgumentFailure(() -> new Rating(first.getSessionId(), teacher, learner, 6, "Too high", java.time.Instant.now()));
        expectArgumentFailure(() -> new Rating(first.getSessionId(), teacher, learner, 5, "  ", java.time.Instant.now()));
        expectArgumentFailure(() -> new Rating(first.getSessionId(), teacher, learner, 5, "x".repeat(1001), java.time.Instant.now()));
        expectArgumentFailure(() -> new Rating(first.getSessionId(), teacher, teacher, 5, "Self review", java.time.Instant.now()));

        sessions.completeSession(first.getSessionId(), teacher.getId());
        Rating teacherReview = ratings.submitRating(first.getSessionId(), teacher.getId(), 5,
                "  Clear, helpful feedback | with detail.  ");
        Rating learnerReview = ratings.submitRating(first.getSessionId(), learner.getId(), 3, "Good session.");
        assert teacherReview.getSubject() == learner;
        assert learnerReview.getSubject() == teacher;
        assert teacherReview.getFeedback().equals("Clear, helpful feedback | with detail.");
        expectStateFailure(() -> ratings.submitRating(first.getSessionId(), teacher.getId(), 4, "Duplicate"));
        expectArgumentFailure(() -> ratings.submitRating(first.getSessionId(), outsider.getId(), 4, "Not a participant"));
        assert ratings.getRatingsForSession(first.getSessionId()).size() == 2;

        PortfolioManager.Portfolio learnerPortfolio = portfolios.getPortfolio(learner.getId());
        assert learnerPortfolio.completedSessions().size() == 1;
        assert learnerPortfolio.skillsToTeach().equals(learner.getSkillsToTeach());
        assert learnerPortfolio.skillsToLearn().equals(learner.getSkillsToLearn());
        assert learnerPortfolio.ratingCount() == 1;
        assert learnerPortfolio.averageRating() == 5.0;
        assert learnerPortfolio.receivedRatings().getFirst().feedback().contains("helpful feedback");
        assert learnerPortfolio.achievements().stream().map(PortfolioManager.Achievement::milestone).toList()
                .equals(List.of(1));
        assert portfolios.getPortfolio(teacher.getId()).averageRating() == 3.0;

        for (int index = 1; index < 5; index++) createCompletedSession(sessions, index, baseDate);
        assert portfolios.getPortfolio(learner.getId()).achievements().stream()
                .map(PortfolioManager.Achievement::milestone).toList().equals(List.of(1, 5));
        for (int index = 5; index < 10; index++) createCompletedSession(sessions, index, baseDate);
        assert portfolios.getPortfolio(learner.getId()).achievements().stream()
                .map(PortfolioManager.Achievement::milestone).toList().equals(List.of(1, 5, 10));
        expectMissingProfile(() -> portfolios.getPortfolio("missing"));

        Path directory = Files.createTempDirectory("skillora-rating-check");
        FileManager files = new FileManager(directory);
        files.saveData(students.getAllStudents(), List.of(), sessions.getAllSessions());
        files.saveRatings(ratings.getAllRatings());
        String notificationData = "existing-notification-data\n";
        Files.writeString(directory.resolve("notifications.txt"), notificationData);
        files.saveRatings(ratings.getAllRatings());
        assert Files.readString(directory.resolve("notifications.txt")).equals(notificationData);
        List<FileManager.SavedRating> savedRatings = files.loadRatings();
        assert savedRatings.size() == 2;
        Path ratingsFile = directory.resolve("ratings.txt");
        List<String> persistedLines = Files.readAllLines(ratingsFile);
        String[] malformedFields = persistedLines.getFirst().split("\\|", -1);
        malformedFields[3] = "0";
        Files.writeString(ratingsFile, String.join("|", malformedFields) + System.lineSeparator()
                + persistedLines.getFirst() + System.lineSeparator());
        List<FileManager.SavedRating> afterMalformedLine = files.loadRatings();
        assert afterMalformedLine.size() == 1;
        assert afterMalformedLine.getFirst().reviewerId().equals(savedRatings.getFirst().reviewerId());

        FileManager.LoadResult savedData = files.loadData();
        StudentManager reloadedStudents = new StudentManager();
        reloadedStudents.loadStudents(savedData.students());
        SessionManager reloadedSessions = new SessionManager(reloadedStudents);
        for (FileManager.SavedSession saved : savedData.sessions()) {
            reloadedSessions.restoreSession(saved.sessionId(), saved.teacherId(), saved.learnerId(), saved.skillName(),
                    saved.date(), saved.startTime(), saved.durationMinutes(), saved.status());
        }
        RatingManager reloadedRatings = new RatingManager(reloadedStudents, reloadedSessions);
        for (FileManager.SavedRating saved : savedRatings) {
            reloadedRatings.restoreRating(saved.sessionId(), saved.reviewerId(), saved.score(),
                    saved.feedback(), saved.createdAt());
        }
        PortfolioManager.Portfolio restoredPortfolio = new PortfolioManager(
                reloadedStudents, reloadedSessions, reloadedRatings).getPortfolio("RATE-L");
        assert restoredPortfolio.ratingCount() == 1;
        assert restoredPortfolio.averageRating() == 5.0;
        assert restoredPortfolio.completedSessions().size() == 10;
        assert restoredPortfolio.achievements().stream().map(PortfolioManager.Achievement::milestone).toList()
                .equals(List.of(1, 5, 10));
        System.out.println("RatingManager and PortfolioManager checks passed.");
    }

    private static Session createCompletedSession(SessionManager sessions, int index, LocalDate baseDate) {
        Session session = sessions.createSession("RATE-T", "RATE-L", "Java", baseDate.plusDays(index),
                LocalTime.of(10, 0), 60);
        sessions.completeSession(session.getSessionId(), "RATE-L");
        return session;
    }

    private static void expectArgumentFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid rating/profile input to fail.");
    }

    private static void expectStateFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected an invalid rating state to fail.");
    }

    private static void expectMissingProfile(Runnable action) {
        try { action.run(); }
        catch (java.util.NoSuchElementException expected) { return; }
        throw new AssertionError("Expected a missing profile to fail.");
    }
}
