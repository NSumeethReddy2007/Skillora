package model;

import java.time.Instant;

public final class RewardTransaction {
    private final int transactionId;
    private final String studentId;
    private final String eventReference;
    private final String activityDescription;
    private final int pointsEarned;
    private final Instant createdAt;

    public RewardTransaction(int transactionId, String studentId, String eventReference,
                             String activityDescription, int pointsEarned, Instant createdAt) {
        if (transactionId < 1) throw new IllegalArgumentException("Transaction ID must be positive.");
        this.transactionId = transactionId;
        this.studentId = requireText(studentId, "Student ID");
        this.eventReference = requireText(eventReference, "Event reference");
        this.activityDescription = requireText(activityDescription, "Activity description");
        if (pointsEarned < 1) throw new IllegalArgumentException("Points earned must be positive.");
        this.pointsEarned = pointsEarned;
        if (createdAt == null) throw new IllegalArgumentException("Transaction timestamp is required.");
        this.createdAt = createdAt;
    }

    public int getTransactionId() { return transactionId; }
    public String getStudentId() { return studentId; }
    public String getEventReference() { return eventReference; }
    public String getActivityDescription() { return activityDescription; }
    public int getPointsEarned() { return pointsEarned; }
    public Instant getCreatedAt() { return createdAt; }

    public RewardTransaction reassignStudent(String newStudentId) {
        return new RewardTransaction(transactionId, newStudentId, eventReference,
                activityDescription, pointsEarned, createdAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }
}
