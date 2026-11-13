package model;

import java.time.Instant;

/** A participant's written rating of the other participant in a completed session. */
public final class Rating {
    private final int sessionId;
    private final Student reviewer;
    private final Student subject;
    private final int score;
    private final String feedback;
    private final Instant createdAt;

    public Rating(int sessionId, Student reviewer, Student subject, int score,
                  String feedback, Instant createdAt) {
        if (sessionId < 1) throw new IllegalArgumentException("Session ID must be positive.");
        if (reviewer == null || subject == null)
            throw new IllegalArgumentException("Reviewer and reviewed student are required.");
        if (reviewer.getId().equalsIgnoreCase(subject.getId()))
            throw new IllegalArgumentException("A student cannot rate themselves.");
        if (score < 1 || score > 5)
            throw new IllegalArgumentException("Rating must be an integer from 1 to 5.");
        if (feedback == null || feedback.isBlank())
            throw new IllegalArgumentException("Written feedback is required.");
        String trimmedFeedback = feedback.trim();
        if (trimmedFeedback.length() > 1000)
            throw new IllegalArgumentException("Feedback must be 1,000 characters or fewer.");
        if (createdAt == null) throw new IllegalArgumentException("Rating timestamp is required.");
        this.sessionId = sessionId;
        this.reviewer = reviewer;
        this.subject = subject;
        this.score = score;
        this.feedback = trimmedFeedback;
        this.createdAt = createdAt;
    }

    public int getSessionId() { return sessionId; }
    public Student getReviewer() { return reviewer; }
    public Student getSubject() { return subject; }
    public int getScore() { return score; }
    public String getFeedback() { return feedback; }
    public Instant getCreatedAt() { return createdAt; }
}
