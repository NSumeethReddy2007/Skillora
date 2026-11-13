package model;

import java.time.LocalDate;

public final class AchievementBadge {
    private final String badgeId;
    private final String studentId;
    private final String badgeName;
    private final LocalDate dateEarned;

    public AchievementBadge(String badgeId, String studentId, String badgeName, LocalDate dateEarned) {
        this.badgeId = requireText(badgeId, "Badge ID");
        this.studentId = requireText(studentId, "Student ID");
        this.badgeName = requireText(badgeName, "Badge name");
        if (dateEarned == null) throw new IllegalArgumentException("Badge earning date is required.");
        this.dateEarned = dateEarned;
    }

    public String getBadgeId() { return badgeId; }
    public String getStudentId() { return studentId; }
    public String getBadgeName() { return badgeName; }
    public LocalDate getDateEarned() { return dateEarned; }

    public AchievementBadge reassignStudent(String newStudentId) {
        return new AchievementBadge(badgeId, newStudentId, badgeName, dateEarned);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }
}
