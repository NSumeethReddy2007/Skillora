package service;

import model.Rating;
import model.Session;
import model.Skill;
import model.Student;

import java.util.List;
import java.util.NoSuchElementException;

/** Builds public portfolio views from the existing student, session, and rating records. */
public final class PortfolioManager {
    private static final List<Integer> SESSION_MILESTONES = List.of(1, 5, 10);
    private final StudentManager studentManager;
    private final SessionManager sessionManager;
    private final RatingManager ratingManager;

    public PortfolioManager(StudentManager studentManager, SessionManager sessionManager,
                            RatingManager ratingManager) {
        if (studentManager == null || sessionManager == null || ratingManager == null)
            throw new IllegalArgumentException("Student, session, and rating managers are required.");
        this.studentManager = studentManager;
        this.sessionManager = sessionManager;
        this.ratingManager = ratingManager;
    }

    public Portfolio getPortfolio(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        Student student = studentManager.findStudentById(studentId.trim())
                .orElseThrow(() -> new NoSuchElementException("Student profile was not found."));
        List<Session> completed = sessionManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == Session.Status.COMPLETED)
                .filter(session -> isParticipant(session, student))
                .toList();
        List<Rating> received = ratingManager.getRatingsAboutStudent(student.getId());
        double average = received.stream().mapToInt(Rating::getScore).average().orElse(0.0);
        List<CompletedSession> completedViews = completed.stream().map(session -> {
            Student other = session.getTeacher().getId().equalsIgnoreCase(student.getId())
                    ? session.getLearner() : session.getTeacher();
            String role = session.getTeacher().getId().equalsIgnoreCase(student.getId()) ? "TEACHER" : "LEARNER";
            return new CompletedSession(session.getSessionId(), role, summary(other), session.getSkill(),
                    session.getDate().toString(), session.getStartTime().toString(), session.getDurationMinutes());
        }).toList();
        List<ReceivedRating> ratingViews = received.stream().map(rating -> {
            Session session = sessionManager.getSession(rating.getSessionId());
            return new ReceivedRating(rating.getSessionId(), session.getSkill().getName(), summary(rating.getReviewer()),
                    rating.getScore(), rating.getFeedback(), rating.getCreatedAt().toString());
        }).toList();
        List<Achievement> achievements = SESSION_MILESTONES.stream()
                .filter(milestone -> completed.size() >= milestone)
                .map(milestone -> new Achievement("COMPLETED_SESSIONS_" + milestone,
                        milestone == 1 ? "First Session" : milestone + " Sessions",
                        "Completed " + milestone + " learning " + (milestone == 1 ? "session" : "sessions") + ".",
                        milestone))
                .toList();
        return new Portfolio(student.getId(), student.getName(), student.getDepartment(), student.getYearOfStudy(),
                student.getSkillsToTeach(), student.getSkillsToLearn(), completedViews, ratingViews,
                average, received.size(), achievements);
    }

    private static boolean isParticipant(Session session, Student student) {
        return session.getTeacher().getId().equalsIgnoreCase(student.getId())
                || session.getLearner().getId().equalsIgnoreCase(student.getId());
    }

    private static StudentSummary summary(Student student) {
        return new StudentSummary(student.getId(), student.getName());
    }

    public record Portfolio(String studentId, String name, String department, int yearOfStudy,
                            List<Skill> skillsToTeach, List<Skill> skillsToLearn,
                            List<CompletedSession> completedSessions, List<ReceivedRating> receivedRatings,
                            double averageRating, int ratingCount, List<Achievement> achievements) {
        public Portfolio {
            skillsToTeach = List.copyOf(skillsToTeach);
            skillsToLearn = List.copyOf(skillsToLearn);
            completedSessions = List.copyOf(completedSessions);
            receivedRatings = List.copyOf(receivedRatings);
            achievements = List.copyOf(achievements);
        }
    }

    public record StudentSummary(String id, String name) { }
    public record CompletedSession(int sessionId, String role, StudentSummary otherParticipant,
                                   Skill skill, String date, String startTime, int durationMinutes) { }
    public record ReceivedRating(int sessionId, String skillName, StudentSummary reviewer,
                                 int score, String feedback, String createdAt) { }
    public record Achievement(String code, String title, String description, int milestone) { }
}
