package service;

import model.Rating;
import model.Session;
import model.Student;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/** Validates and stores one rating per participant for each completed session. */
public final class RatingManager {
    private final StudentManager studentManager;
    private final SessionManager sessionManager;
    private final ArrayList<Rating> ratings = new ArrayList<>();

    public RatingManager(StudentManager studentManager, SessionManager sessionManager) {
        if (studentManager == null || sessionManager == null)
            throw new IllegalArgumentException("Student and session managers are required.");
        this.studentManager = studentManager;
        this.sessionManager = sessionManager;
    }

    public Rating submitRating(int sessionId, String reviewerId, int score, String feedback) {
        Session session = sessionManager.getSession(sessionId);
        if (session.getStatus() != Session.Status.COMPLETED)
            throw new IllegalStateException("Only completed sessions can be rated.");
        if (reviewerId == null || reviewerId.isBlank())
            throw new IllegalArgumentException("Reviewer student ID is required.");
        Student reviewer = studentManager.findStudentById(reviewerId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Reviewer student ID was not found."));
        Student subject;
        if (session.getTeacher().getId().equalsIgnoreCase(reviewer.getId())) {
            subject = session.getLearner();
        } else if (session.getLearner().getId().equalsIgnoreCase(reviewer.getId())) {
            subject = session.getTeacher();
        } else {
            throw new IllegalArgumentException("Only a session participant can submit a rating.");
        }
        ensureNotRated(sessionId, reviewer.getId());
        Rating rating = new Rating(sessionId, reviewer, subject, score, feedback, Instant.now());
        ratings.add(rating);
        return rating;
    }

    public List<Rating> getRatingsForSession(int sessionId) {
        sessionManager.getSession(sessionId);
        return ratings.stream().filter(rating -> rating.getSessionId() == sessionId)
                .sorted(Comparator.comparing(Rating::getCreatedAt)
                        .thenComparing(rating -> rating.getReviewer().getId(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<Rating> getRatingsAboutStudent(String studentId) {
        Student student = findStudent(studentId, "Student");
        return ratings.stream().filter(rating -> rating.getSubject().getId().equalsIgnoreCase(student.getId()))
                .sorted(Comparator.comparing(Rating::getCreatedAt).reversed()).toList();
    }

    public List<Rating> getAllRatings() { return List.copyOf(ratings); }

    public void restoreRating(int sessionId, String reviewerId, int score,
                              String feedback, Instant createdAt) {
        Session session = sessionManager.getSession(sessionId);
        if (session.getStatus() != Session.Status.COMPLETED)
            throw new IllegalStateException("Only completed sessions can have ratings.");
        Student reviewer = findStudent(reviewerId, "Reviewer");
        Student subject;
        if (session.getTeacher().getId().equalsIgnoreCase(reviewer.getId())) {
            subject = session.getLearner();
        } else if (session.getLearner().getId().equalsIgnoreCase(reviewer.getId())) {
            subject = session.getTeacher();
        } else {
            throw new IllegalArgumentException("Reviewer is not a participant in this session.");
        }
        ensureNotRated(sessionId, reviewer.getId());
        ratings.add(new Rating(sessionId, reviewer, subject, score, feedback, createdAt));
    }

    public void discardRating(int sessionId, String reviewerId) {
        if (reviewerId == null) return;
        ratings.removeIf(rating -> rating.getSessionId() == sessionId
                && rating.getReviewer().getId().equalsIgnoreCase(reviewerId.trim()));
    }

    private void ensureNotRated(int sessionId, String reviewerId) {
        if (ratings.stream().anyMatch(rating -> rating.getSessionId() == sessionId
                && rating.getReviewer().getId().equalsIgnoreCase(reviewerId)))
            throw new IllegalStateException("This participant has already rated the session.");
    }

    private Student findStudent(String studentId, String role) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException(role + " student ID is required.");
        return studentManager.findStudentById(studentId.trim())
                .orElseThrow(() -> new NoSuchElementException(role + " student was not found."));
    }
}
