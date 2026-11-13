package service;

import model.AchievementBadge;
import model.LearningRoadmap;
import model.RewardTransaction;
import model.Session;
import model.Student;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

public final class RewardManager {
    private static final List<BadgeDefinition> BADGE_DEFINITIONS = List.of(
            new BadgeDefinition("FIRST_SESSION", "First Session",
                    "Complete 1 skill-exchange session."),
            new BadgeDefinition("ACTIVE_LEARNER", "Active Learner",
                    "Complete 5 skill-exchange sessions."),
            new BadgeDefinition("SKILL_EXPLORER", "Skill Explorer",
                    "Complete 10 skill-exchange sessions."),
            new BadgeDefinition("FIRST_MILESTONE", "First Milestone",
                    "Complete the first roadmap milestone."),
            new BadgeDefinition("ROADMAP_CHAMPION", "Roadmap Champion",
                    "Complete an entire learning roadmap."));
    private static final List<Integer> SESSION_THRESHOLDS = List.of(1, 5, 10);

    private final StudentManager studentManager;
    private final SessionManager sessionManager;
    private final LearningRoadmapManager roadmapManager;
    private final ArrayList<RewardTransaction> transactions = new ArrayList<>();
    private final ArrayList<AchievementBadge> badges = new ArrayList<>();
    private int nextTransactionId = 1;

    public RewardManager(StudentManager studentManager, SessionManager sessionManager,
                         LearningRoadmapManager roadmapManager) {
        if (studentManager == null || sessionManager == null || roadmapManager == null)
            throw new IllegalArgumentException("Student, session, and roadmap managers are required.");
        this.studentManager = studentManager;
        this.sessionManager = sessionManager;
        this.roadmapManager = roadmapManager;
    }

    public List<RewardTransaction> getAllTransactions() {
        return transactions.stream()
                .sorted(Comparator.comparing(RewardTransaction::getCreatedAt).reversed()
                        .thenComparing(Comparator.comparingInt(RewardTransaction::getTransactionId).reversed()))
                .toList();
    }

    public List<RewardTransaction> getTransactionsForStudent(String studentId) {
        Student student = findStudent(studentId);
        return getAllTransactions().stream()
                .filter(transaction -> transaction.getStudentId().equalsIgnoreCase(student.getId()))
                .toList();
    }

    public List<AchievementBadge> getAllBadges() {
        return badges.stream().sorted(Comparator.comparing(AchievementBadge::getDateEarned)
                .thenComparing(AchievementBadge::getBadgeId)).toList();
    }

    public List<AchievementBadge> getBadgesForStudent(String studentId) {
        Student student = findStudent(studentId);
        return getAllBadges().stream()
                .filter(badge -> badge.getStudentId().equalsIgnoreCase(student.getId()))
                .toList();
    }

    public List<BadgeDefinition> getBadgeDefinitions() {
        return BADGE_DEFINITIONS;
    }

    public long getTotalPoints(String studentId) {
        Student student = findStudent(studentId);
        return transactions.stream()
                .filter(transaction -> transaction.getStudentId().equalsIgnoreCase(student.getId()))
                .mapToLong(RewardTransaction::getPointsEarned)
                .reduce(0L, Math::addExact);
    }

    public int awardCompletedSession(Session session) {
        if (session == null) throw new IllegalArgumentException("Session is required.");
        if (sessionManager.getSession(session.getSessionId()) != session)
            throw new IllegalArgumentException("Session must be managed by the active session service.");
        if (session.getStatus() != Session.Status.COMPLETED)
            throw new IllegalStateException("Only completed sessions can earn reward points.");
        Student teacher = findStudent(session.getTeacher().getId());
        Student learner = findStudent(session.getLearner().getId());
        String eventReference = "SESSION_COMPLETED:" + session.getSessionId();
        int missingAwards = (hasEvent(teacher.getId(), eventReference) ? 0 : 1)
                + (hasEvent(learner.getId(), eventReference) ? 0 : 1);
        ensureTransactionCapacity(missingAwards);
        int awarded = 0;
        if (awardPoints(teacher.getId(), eventReference,
                "Completed skill-exchange session #" + session.getSessionId() + ".", 10)) awarded++;
        if (awardPoints(learner.getId(), eventReference,
                "Completed skill-exchange session #" + session.getSessionId() + ".", 10)) awarded++;
        updateBadges(teacher.getId());
        updateBadges(learner.getId());
        return awarded;
    }

    public int awardCompletedMilestone(int roadmapId, int milestoneId) {
        LearningRoadmap roadmap = roadmapManager.getRoadmap(roadmapId);
        Student student = findStudent(roadmap.getStudentId());
        if (!roadmap.getMilestone(milestoneId).isCompleted())
            throw new IllegalStateException("Only completed roadmap milestones can earn reward points.");
        String milestoneReference = "ROADMAP_MILESTONE:" + roadmapId + ":" + milestoneId;
        String completionReference = "ROADMAP_COMPLETED:" + roadmapId;
        int missingAwards = (hasEvent(student.getId(), milestoneReference) ? 0 : 1)
                + (roadmap.getStatus() == LearningRoadmap.Status.COMPLETED
                && !hasEvent(student.getId(), completionReference) ? 1 : 0);
        ensureTransactionCapacity(missingAwards);
        int awarded = awardPoints(student.getId(),
                milestoneReference,
                "Completed milestone \"" + roadmap.getMilestone(milestoneId).getTitle()
                        + "\" in roadmap \"" + roadmap.getTitle() + "\".", 5) ? 1 : 0;
        if (roadmap.getStatus() == LearningRoadmap.Status.COMPLETED
                && awardPoints(student.getId(), completionReference,
                "Completed learning roadmap \"" + roadmap.getTitle() + "\".", 20)) {
            awarded++;
        }
        updateBadges(student.getId());
        return awarded;
    }

    public int synchronizeCompletedActivities() {
        int awarded = 0;
        for (Session session : sessionManager.getAllSessions()) {
            if (session.getStatus() == Session.Status.COMPLETED)
                awarded += awardCompletedSession(session);
        }
        for (LearningRoadmap roadmap : roadmapManager.getAllRoadmaps()) {
            for (var milestone : roadmap.getMilestones()) {
                if (milestone.isCompleted())
                    awarded += awardCompletedMilestone(roadmap.getRoadmapId(), milestone.getMilestoneId());
            }
        }
        return awarded;
    }

    public ProgressSummary getProgressSummary(String studentId) {
        Student student = findStudent(studentId);
        int completedSessions = (int) sessionManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == Session.Status.COMPLETED)
                .filter(session -> isParticipant(session, student.getId()))
                .count();
        int completedMilestones = roadmapManager.getRoadmapsForStudent(student.getId()).stream()
                .flatMap(roadmap -> roadmap.getMilestones().stream())
                .mapToInt(milestone -> milestone.isCompleted() ? 1 : 0).sum();
        int completedRoadmaps = (int) roadmapManager.getRoadmapsForStudent(student.getId()).stream()
                .filter(roadmap -> roadmap.getStatus() == LearningRoadmap.Status.COMPLETED).count();
        int bestRoadmapProgress = roadmapManager.getRoadmapsForStudent(student.getId()).stream()
                .mapToInt(LearningRoadmap::getProgressPercent).max().orElse(0);
        int nextSessionThreshold = SESSION_THRESHOLDS.stream()
                .filter(threshold -> completedSessions < threshold).findFirst().orElse(10);
        return new ProgressSummary(completedSessions, nextSessionThreshold, completedMilestones,
                completedRoadmaps, bestRoadmapProgress);
    }

    public List<LeaderboardEntry> getLeaderboard() {
        List<Student> students = studentManager.getAllStudents();
        List<LeaderboardEntry> sorted = students.stream().map(student -> {
            int completedSessions = (int) sessionManager.getAllSessions().stream()
                    .filter(session -> session.getStatus() == Session.Status.COMPLETED)
                    .filter(session -> isParticipant(session, student.getId())).count();
            int completedMilestones = roadmapManager.getRoadmapsForStudent(student.getId()).stream()
                    .flatMap(roadmap -> roadmap.getMilestones().stream())
                    .mapToInt(milestone -> milestone.isCompleted() ? 1 : 0).sum();
            int earnedBadges = (int) badges.stream()
                    .filter(badge -> badge.getStudentId().equalsIgnoreCase(student.getId())).count();
            return new LeaderboardEntry(0, student, getTotalPoints(student.getId()),
                    completedSessions, completedMilestones, earnedBadges);
        }).sorted(Comparator.comparingLong(LeaderboardEntry::totalPoints).reversed()
                .thenComparing(entry -> entry.student().getName(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.student().getId(), String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<LeaderboardEntry> ranked = new ArrayList<>(sorted.size());
        long previousPoints = Long.MIN_VALUE;
        int rank = 0;
        for (int index = 0; index < sorted.size(); index++) {
            LeaderboardEntry entry = sorted.get(index);
            if (entry.totalPoints() != previousPoints) rank = index + 1;
            ranked.add(new LeaderboardEntry(rank, entry.student(), entry.totalPoints(),
                    entry.completedSessions(), entry.completedMilestones(), entry.earnedBadges()));
            previousPoints = entry.totalPoints();
        }
        return List.copyOf(ranked);
    }

    public boolean hasRewardsForStudent(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        String normalizedId = studentId.trim();
        return transactions.stream().anyMatch(transaction ->
                transaction.getStudentId().equalsIgnoreCase(normalizedId))
                || badges.stream().anyMatch(badge -> badge.getStudentId().equalsIgnoreCase(normalizedId));
    }

    public void updateStudentId(String oldStudentId, String newStudentId) {
        if (oldStudentId == null || oldStudentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        Student updatedStudent = findStudent(newStudentId);
        String previousId = oldStudentId.trim();
        for (int index = 0; index < transactions.size(); index++) {
            RewardTransaction transaction = transactions.get(index);
            if (transaction.getStudentId().equalsIgnoreCase(previousId))
                transactions.set(index, transaction.reassignStudent(updatedStudent.getId()));
        }
        for (int index = 0; index < badges.size(); index++) {
            AchievementBadge badge = badges.get(index);
            if (badge.getStudentId().equalsIgnoreCase(previousId))
                badges.set(index, badge.reassignStudent(updatedStudent.getId()));
        }
    }

    public void restoreTransaction(int transactionId, String studentId, String eventReference,
                                   String description, int points, Instant createdAt) {
        Student student = findStudent(studentId);
        if (transactionId == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Reward transaction ID is too large to restore.");
        RewardTransaction transaction = new RewardTransaction(transactionId, student.getId(),
                eventReference, description, points, createdAt);
        if (transactions.stream().anyMatch(item -> item.getTransactionId() == transactionId))
            throw new IllegalArgumentException("Reward data contains a duplicate transaction ID.");
        if (hasEvent(student.getId(), eventReference))
            throw new IllegalArgumentException("Reward data contains a duplicate activity transaction.");
        transactions.add(transaction);
        nextTransactionId = Math.max(nextTransactionId, Math.incrementExact(transactionId));
    }

    public void restoreBadge(String badgeId, String studentId, String badgeName, LocalDate dateEarned) {
        Student student = findStudent(studentId);
        BadgeDefinition definition = getBadgeDefinition(badgeId);
        if (!definition.name().equals(badgeName))
            throw new IllegalArgumentException("Saved badge name does not match its badge ID.");
        if (hasBadge(student.getId(), badgeId))
            throw new IllegalArgumentException("Reward data contains a duplicate badge award.");
        badges.add(new AchievementBadge(badgeId, student.getId(), badgeName, dateEarned));
    }

    public RewardState snapshot() {
        return new RewardState(transactions, badges, nextTransactionId);
    }

    public void restoreState(RewardState state) {
        if (state == null) throw new IllegalArgumentException("Reward state is required.");
        transactions.clear();
        transactions.addAll(state.transactions());
        badges.clear();
        badges.addAll(state.badges());
        nextTransactionId = state.nextTransactionId();
    }

    private void updateBadges(String studentId) {
        Student student = findStudent(studentId);
        int completedSessions = (int) sessionManager.getAllSessions().stream()
                .filter(session -> session.getStatus() == Session.Status.COMPLETED)
                .filter(session -> isParticipant(session, student.getId())).count();
        if (completedSessions >= 1) awardBadge(student.getId(), "FIRST_SESSION");
        if (completedSessions >= 5) awardBadge(student.getId(), "ACTIVE_LEARNER");
        if (completedSessions >= 10) awardBadge(student.getId(), "SKILL_EXPLORER");

        List<LearningRoadmap> roadmaps = roadmapManager.getRoadmapsForStudent(student.getId());
        boolean hasCompletedMilestone = roadmaps.stream()
                .flatMap(roadmap -> roadmap.getMilestones().stream())
                .anyMatch(milestone -> milestone.isCompleted());
        if (hasCompletedMilestone) awardBadge(student.getId(), "FIRST_MILESTONE");
        if (roadmaps.stream().anyMatch(roadmap -> roadmap.getStatus() == LearningRoadmap.Status.COMPLETED))
            awardBadge(student.getId(), "ROADMAP_CHAMPION");
    }

    private void awardBadge(String studentId, String badgeId) {
        if (hasBadge(studentId, badgeId)) return;
        BadgeDefinition definition = getBadgeDefinition(badgeId);
        badges.add(new AchievementBadge(definition.id(), studentId, definition.name(), LocalDate.now()));
    }

    private boolean awardPoints(String studentId, String eventReference, String description, int points) {
        if (hasEvent(studentId, eventReference)) return false;
        RewardTransaction transaction = new RewardTransaction(nextTransactionId, studentId, eventReference,
                description, points, Instant.now());
        transactions.add(transaction);
        nextTransactionId = Math.incrementExact(nextTransactionId);
        return true;
    }

    private void ensureTransactionCapacity(int requiredAwards) {
        if (requiredAwards > 0
                && (long) nextTransactionId + requiredAwards - 1 > Integer.MAX_VALUE - 1L)
            throw new IllegalStateException("Reward transaction IDs are exhausted.");
    }

    private boolean hasEvent(String studentId, String eventReference) {
        return transactions.stream().anyMatch(transaction ->
                transaction.getStudentId().equalsIgnoreCase(studentId)
                        && transaction.getEventReference().equalsIgnoreCase(eventReference));
    }

    private boolean hasBadge(String studentId, String badgeId) {
        return badges.stream().anyMatch(badge -> badge.getStudentId().equalsIgnoreCase(studentId)
                && badge.getBadgeId().equalsIgnoreCase(badgeId));
    }

    private static BadgeDefinition getBadgeDefinition(String badgeId) {
        return BADGE_DEFINITIONS.stream().filter(definition -> definition.id().equalsIgnoreCase(badgeId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown achievement badge ID."));
    }

    private Student findStudent(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        return studentManager.findStudentById(studentId.trim())
                .orElseThrow(() -> new NoSuchElementException("Student profile was not found."));
    }

    private static boolean isParticipant(Session session, String studentId) {
        return session.getTeacher().getId().equalsIgnoreCase(studentId)
                || session.getLearner().getId().equalsIgnoreCase(studentId);
    }

    public record BadgeDefinition(String id, String name, String description) { }
    public record ProgressSummary(int completedSessions, int nextSessionThreshold,
                                  int completedMilestones, int completedRoadmaps,
                                  int bestRoadmapProgress) { }
    public record LeaderboardEntry(int rank, Student student, long totalPoints,
                                   int completedSessions, int completedMilestones, int earnedBadges) { }
    public record RewardState(List<RewardTransaction> transactions, List<AchievementBadge> badges,
                              int nextTransactionId) {
        public RewardState {
            transactions = List.copyOf(transactions);
            badges = List.copyOf(badges);
            if (nextTransactionId < 1) throw new IllegalArgumentException("Next transaction ID must be positive.");
        }
    }
}
