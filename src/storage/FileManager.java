package storage;

import model.ExchangeRequest;
import model.Skill;
import model.Student;
import model.Session;
import model.Notification;
import model.Rating;
import model.LearningRoadmap;
import model.RoadmapMilestone;
import model.RewardTransaction;
import model.AchievementBadge;
import model.LearningGroup;
import model.GroupLearningSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;

public class FileManager {
    private static final String STUDENTS_FILE = "students.txt";
    private static final String REQUESTS_FILE = "requests.txt";
    private static final String SESSIONS_FILE = "sessions.txt";
    private static final String NOTIFICATIONS_FILE = "notifications.txt";
    private static final String RATINGS_FILE = "ratings.txt";
    private static final String ROADMAPS_FILE = "roadmaps.txt";
    private static final String REWARDS_FILE = "rewards.txt";
    private static final String GROUPS_FILE = "learning-groups.txt";
    private static final String GROUP_SESSIONS_FILE = "group-learning-sessions.txt";
    private final Path dataDirectory;
    private final PersistenceFailureInjector persistenceFailureInjector;

    public FileManager() {
        this(Path.of(System.getProperty("user.dir"), "data"));
    }

    public FileManager(Path dataDirectory) {
        this(dataDirectory, stage -> { });
    }

    public FileManager(Path dataDirectory, PersistenceFailureInjector persistenceFailureInjector) {
        if (dataDirectory == null) {
            throw new IllegalArgumentException("Data directory is required.");
        }
        if (persistenceFailureInjector == null) {
            throw new IllegalArgumentException("Persistence failure injector is required.");
        }
        this.dataDirectory = dataDirectory;
        this.persistenceFailureInjector = persistenceFailureInjector;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public LoadResult loadData() throws IOException {
        List<String> warnings = new ArrayList<>();
        List<Student> students = loadStudents(warnings);
        List<SavedRequest> requests = loadRequests(warnings);
        List<SavedSession> sessions = loadSessions(warnings);
        return new LoadResult(students, requests, sessions, warnings);
    }

    public AllData loadAllData() throws IOException {
        LoadResult coreData = loadData();
        List<String> warnings = new ArrayList<>(coreData.warnings());
        List<SavedRating> ratings = loadRatings(warnings);
        List<SavedNotification> notifications = loadNotifications(warnings);
        List<SavedRoadmap> roadmaps = loadRoadmaps(warnings);
        LoadedRewards rewards = loadRewards(warnings);
        List<SavedLearningGroup> groups = loadLearningGroups(warnings);
        List<SavedGroupLearningSession> groupSessions = loadGroupLearningSessions(warnings);
        return new AllData(coreData.students(), coreData.requests(), coreData.sessions(),
                notifications, ratings, roadmaps, rewards.transactions(), rewards.badges(),
                groups, groupSessions, warnings);
    }

    public List<SavedNotification> loadNotifications() throws IOException {
        return loadNotifications(null);
    }

    private List<SavedNotification> loadNotifications(List<String> warnings) throws IOException {
        Path file = dataDirectory.resolve(NOTIFICATIONS_FILE);
        if (!Files.exists(file)) return List.of();
        List<SavedNotification> result = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            try {
                String[] fields = lines.get(index).split("\\|", -1);
                if (fields.length != 7 || !"N".equals(fields[0]))
                    throw new IllegalArgumentException("Invalid notification record.");
                int id = parsePositiveInteger(fields[1], "notification ID");
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate notification ID.");
                if (!"true".equals(fields[6]) && !"false".equals(fields[6]))
                    throw new IllegalArgumentException("Invalid notification read status.");
                result.add(new SavedNotification(id, decode(fields[2]), decode(fields[3]),
                        decode(fields[4]), Instant.parse(fields[5]), Boolean.parseBoolean(fields[6])));
            } catch (RuntimeException exception) {
                addLoadWarning(warnings, NOTIFICATIONS_FILE, index, exception);
            }
        }
        return List.copyOf(result);
    }

    public void saveNotifications(List<Notification> notifications) throws IOException {
        if (notifications == null) throw new IllegalArgumentException("Notification data is required.");
        Files.createDirectories(dataDirectory);
        writeAtomically(dataDirectory.resolve(NOTIFICATIONS_FILE), serializeNotifications(notifications));
    }

    public List<SavedRating> loadRatings() throws IOException {
        return loadRatings(null);
    }

    private List<SavedRating> loadRatings(List<String> warnings) throws IOException {
        Path file = dataDirectory.resolve(RATINGS_FILE);
        if (!Files.exists(file)) return List.of();
        List<SavedRating> result = new ArrayList<>();
        Set<String> uniqueReviewerSessions = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            try {
                String[] fields = lines.get(index).split("\\|", -1);
                if (fields.length != 6 || !"V".equals(fields[0]))
                    throw new IllegalArgumentException("Invalid rating record.");
                int sessionId = parsePositiveInteger(fields[1], "session ID");
                String reviewerId = decode(fields[2]);
                if (reviewerId.isBlank()) throw new IllegalArgumentException("Reviewer student ID is required.");
                String key = sessionId + ":" + reviewerId.toLowerCase(java.util.Locale.ROOT);
                if (uniqueReviewerSessions.contains(key))
                    throw new IllegalArgumentException("Duplicate rating for this reviewer and session.");
                int score = parsePositiveInteger(fields[3], "rating");
                if (score > 5) throw new IllegalArgumentException("Rating must be an integer from 1 to 5.");
                String feedback = decode(fields[4]);
                if (feedback.isBlank()) throw new IllegalArgumentException("Written feedback is required.");
                if (feedback.trim().length() > 1000)
                    throw new IllegalArgumentException("Feedback must be 1,000 characters or fewer.");
                SavedRating rating = new SavedRating(sessionId, reviewerId, score, feedback, Instant.parse(fields[5]));
                result.add(rating);
                uniqueReviewerSessions.add(key);
            } catch (RuntimeException exception) {
                addLoadWarning(warnings, RATINGS_FILE, index, exception);
            }
        }
        return List.copyOf(result);
    }

    private static void addLoadWarning(List<String> warnings, String fileName, int zeroBasedLine,
                                       RuntimeException exception) {
        String warning = fileName + " line " + (zeroBasedLine + 1) + ": "
                + exception.getMessage() + " Skipped.";
        if (warnings == null) {
            System.err.println("Data warning: " + warning);
        } else {
            warnings.add(warning);
        }
    }

    public void saveRatings(List<Rating> ratings) throws IOException {
        if (ratings == null) throw new IllegalArgumentException("Rating data is required.");
        Files.createDirectories(dataDirectory);
        writeAtomically(dataDirectory.resolve(RATINGS_FILE), serializeRatings(ratings));
    }

    private List<String> serializeRatings(List<Rating> ratings) {
        Set<String> uniqueReviewerSessions = new HashSet<>();
        List<String> lines = new ArrayList<>();
        for (Rating rating : ratings) {
            if (rating == null) throw new IllegalArgumentException("Rating data cannot contain null records.");
            String key = rating.getSessionId() + ":"
                    + rating.getReviewer().getId().toLowerCase(java.util.Locale.ROOT);
            if (!uniqueReviewerSessions.add(key))
                throw new IllegalArgumentException("Rating data contains a duplicate reviewer and session.");
            lines.add(String.join("|", "V", Integer.toString(rating.getSessionId()),
                    encode(rating.getReviewer().getId()), Integer.toString(rating.getScore()),
                    encode(rating.getFeedback()), rating.getCreatedAt().toString()));
        }
        return List.copyOf(lines);
    }

    private List<String> serializeNotifications(List<Notification> notifications) {
        return notifications.stream().map(notification -> String.join("|", "N",
                Integer.toString(notification.getNotificationId()), encode(notification.getRecipientId()),
                encode(notification.getEventType()), encode(notification.getMessage()),
                notification.getCreatedAt().toString(), Boolean.toString(notification.isRead()))).toList();
    }

    private List<SavedRoadmap> loadRoadmaps(List<String> warnings) throws IOException {
        Path file = dataDirectory.resolve(ROADMAPS_FILE);
        if (!Files.exists(file)) return List.of();
        Map<Integer, SavedRoadmapBuilder> roadmaps = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> milestoneIds = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            try {
                String[] fields = lines.get(index).split("\\|", -1);
                if ("P".equals(fields[0])) {
                    if (fields.length != 8) throw new IllegalArgumentException("Invalid roadmap record.");
                    int id = parsePositiveInteger(fields[1], "roadmap ID");
                    SavedRoadmapBuilder builder = new SavedRoadmapBuilder(id, decode(fields[2]),
                            decode(fields[3]), decode(fields[4]), decode(fields[5]),
                            LocalDate.parse(fields[6]), LocalDate.parse(fields[7]));
                    if (roadmaps.putIfAbsent(id, builder) != null)
                        throw new IllegalArgumentException("Duplicate roadmap ID.");
                    milestoneIds.put(id, new HashSet<>());
                } else if ("Q".equals(fields[0])) {
                    if (fields.length != 7) throw new IllegalArgumentException("Invalid roadmap milestone record.");
                    int roadmapId = parsePositiveInteger(fields[1], "roadmap ID");
                    int milestoneId = parsePositiveInteger(fields[2], "milestone ID");
                    if (!"true".equals(fields[6]) && !"false".equals(fields[6]))
                        throw new IllegalArgumentException("Invalid milestone completion status.");
                    SavedRoadmapBuilder roadmap = roadmaps.get(roadmapId);
                    if (roadmap == null)
                        throw new IllegalArgumentException("Milestone references a missing or later roadmap.");
                    if (!milestoneIds.get(roadmapId).add(milestoneId))
                        throw new IllegalArgumentException("Duplicate milestone ID.");
                    roadmap.milestones.add(new SavedRoadmapMilestone(milestoneId,
                            decode(fields[3]), decode(fields[4]), LocalDate.parse(fields[5]),
                            Boolean.parseBoolean(fields[6])));
                } else {
                    throw new IllegalArgumentException("Unknown roadmap record type.");
                }
            } catch (RuntimeException exception) {
                addLoadWarning(warnings, ROADMAPS_FILE, index, exception);
            }
        }
        return roadmaps.values().stream().map(SavedRoadmapBuilder::build).toList();
    }

    private List<String> serializeRoadmaps(List<LearningRoadmap> roadmaps) {
        List<String> lines = new ArrayList<>();
        for (LearningRoadmap roadmap : roadmaps) {
            lines.add(String.join("|", "P", Integer.toString(roadmap.getRoadmapId()),
                    encode(roadmap.getStudentId()), encode(roadmap.getTitle()), encode(roadmap.getTargetSkill()),
                    encode(roadmap.getDescription()), roadmap.getCreationDate().toString(),
                    roadmap.getTargetCompletionDate().toString()));
            for (RoadmapMilestone milestone : roadmap.getMilestones()) {
                lines.add(String.join("|", "Q", Integer.toString(roadmap.getRoadmapId()),
                        Integer.toString(milestone.getMilestoneId()), encode(milestone.getTitle()),
                        encode(milestone.getDescription()), milestone.getDueDate().toString(),
                        Boolean.toString(milestone.isCompleted())));
            }
        }
        return lines;
    }

    private LoadedRewards loadRewards(List<String> warnings) throws IOException {
        Path file = dataDirectory.resolve(REWARDS_FILE);
        if (!Files.exists(file)) return new LoadedRewards(List.of(), List.of());
        List<SavedRewardTransaction> transactions = new ArrayList<>();
        List<SavedBadge> badges = new ArrayList<>();
        Set<Integer> transactionIds = new HashSet<>();
        Set<String> eventReferences = new HashSet<>();
        Set<String> badgeAwards = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            try {
                String[] fields = lines.get(index).split("\\|", -1);
                if ("T".equals(fields[0])) {
                    if (fields.length != 7) throw new IllegalArgumentException("Invalid reward transaction record.");
                    int transactionId = parsePositiveInteger(fields[1], "transaction ID");
                    if (transactionIds.contains(transactionId))
                        throw new IllegalArgumentException("Duplicate reward transaction ID.");
                    String studentId = decode(fields[2]);
                    String eventReference = decode(fields[3]);
                    if (studentId.isBlank() || eventReference.isBlank())
                        throw new IllegalArgumentException("Student ID and event reference are required.");
                    String uniqueEvent = studentId.toLowerCase(java.util.Locale.ROOT) + ":"
                            + eventReference.toLowerCase(java.util.Locale.ROOT);
                    if (eventReferences.contains(uniqueEvent))
                        throw new IllegalArgumentException("Duplicate reward activity transaction.");
                    String description = decode(fields[4]);
                    int points = parsePositiveInteger(fields[5], "points earned");
                    SavedRewardTransaction transaction = new SavedRewardTransaction(transactionId, studentId,
                            eventReference, description, points, Instant.parse(fields[6]));
                    transactions.add(transaction);
                    transactionIds.add(transactionId);
                    eventReferences.add(uniqueEvent);
                } else if ("B".equals(fields[0])) {
                    if (fields.length != 5) throw new IllegalArgumentException("Invalid achievement badge record.");
                    String badgeId = decode(fields[1]);
                    String studentId = decode(fields[2]);
                    String badgeName = decode(fields[3]);
                    if (badgeId.isBlank() || studentId.isBlank() || badgeName.isBlank())
                        throw new IllegalArgumentException("Badge ID, student ID, and name are required.");
                    String uniqueBadge = studentId.toLowerCase(java.util.Locale.ROOT) + ":"
                            + badgeId.toLowerCase(java.util.Locale.ROOT);
                    if (badgeAwards.contains(uniqueBadge))
                        throw new IllegalArgumentException("Duplicate achievement badge award.");
                    SavedBadge badge = new SavedBadge(badgeId, studentId, badgeName, LocalDate.parse(fields[4]));
                    badges.add(badge);
                    badgeAwards.add(uniqueBadge);
                } else {
                    throw new IllegalArgumentException("Unknown reward record type.");
                }
            } catch (RuntimeException exception) {
                addLoadWarning(warnings, REWARDS_FILE, index, exception);
            }
        }
        return new LoadedRewards(List.copyOf(transactions), List.copyOf(badges));
    }

    private List<String> serializeRewards(List<RewardTransaction> transactions, List<AchievementBadge> badges) {
        List<String> lines = new ArrayList<>();
        for (RewardTransaction transaction : transactions) {
            lines.add(String.join("|", "T", Integer.toString(transaction.getTransactionId()),
                    encode(transaction.getStudentId()), encode(transaction.getEventReference()),
                    encode(transaction.getActivityDescription()), Integer.toString(transaction.getPointsEarned()),
                    transaction.getCreatedAt().toString()));
        }
        for (AchievementBadge badge : badges) {
            lines.add(String.join("|", "B", encode(badge.getBadgeId()), encode(badge.getStudentId()),
                    encode(badge.getBadgeName()), badge.getDateEarned().toString()));
        }
        return lines;
    }

    private List<SavedLearningGroup> loadLearningGroups(List<String> warnings) throws IOException {
        Path file = dataDirectory.resolve(GROUPS_FILE);
        if (!Files.exists(file)) return List.of();
        Map<Integer, SavedLearningGroupBuilder> groups = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            try {
                String[] fields = lines.get(index).split("\\|", -1);
                if ("G".equals(fields[0])) {
                    if (fields.length != 9) throw new IllegalArgumentException("Invalid learning group record.");
                    int id = parsePositiveInteger(fields[1], "group ID");
                    SavedLearningGroupBuilder builder = new SavedLearningGroupBuilder(id, decode(fields[2]),
                            decode(fields[3]), decode(fields[4]), decode(fields[5]), LocalDate.parse(fields[6]),
                            parsePositiveInteger(fields[7], "maximum member count"),
                            LearningGroup.Status.valueOf(fields[8]));
                    if (groups.putIfAbsent(id, builder) != null)
                        throw new IllegalArgumentException("Duplicate learning group ID.");
                } else if ("M".equals(fields[0])) {
                    if (fields.length != 3) throw new IllegalArgumentException("Invalid group member record.");
                    int groupId = parsePositiveInteger(fields[1], "group ID");
                    SavedLearningGroupBuilder group = groups.get(groupId);
                    if (group == null)
                        throw new IllegalArgumentException("Group member references a missing or later group.");
                    String memberId = decode(fields[2]);
                    if (memberId.isBlank() || group.memberIds.stream()
                            .anyMatch(existing -> existing.equalsIgnoreCase(memberId)))
                        throw new IllegalArgumentException("Invalid or duplicate group membership.");
                    group.memberIds.add(memberId);
                } else {
                    throw new IllegalArgumentException("Unknown learning group record type.");
                }
            } catch (RuntimeException exception) {
                addLoadWarning(warnings, GROUPS_FILE, index, exception);
            }
        }
        return groups.values().stream().map(SavedLearningGroupBuilder::build).toList();
    }

    private List<SavedGroupLearningSession> loadGroupLearningSessions(List<String> warnings) throws IOException {
        Path file = dataDirectory.resolve(GROUP_SESSIONS_FILE);
        if (!Files.exists(file)) return List.of();
        List<SavedGroupLearningSession> sessions = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            try {
                String[] fields = lines.get(index).split("\\|", -1);
                if (fields.length != 10 || !"C".equals(fields[0]))
                    throw new IllegalArgumentException("Invalid group learning session record.");
                int id = parsePositiveInteger(fields[1], "group session ID");
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate group session ID.");
                sessions.add(new SavedGroupLearningSession(id,
                        parsePositiveInteger(fields[2], "group ID"), decode(fields[3]), decode(fields[4]),
                        LocalDate.parse(fields[5]), LocalTime.parse(fields[6]),
                        parsePositiveInteger(fields[7], "duration"), decode(fields[8]),
                        GroupLearningSession.Status.valueOf(fields[9])));
            } catch (RuntimeException exception) {
                addLoadWarning(warnings, GROUP_SESSIONS_FILE, index, exception);
            }
        }
        return List.copyOf(sessions);
    }

    private List<String> serializeLearningGroups(List<LearningGroup> groups) {
        List<String> lines = new ArrayList<>();
        for (LearningGroup group : groups) {
            lines.add(String.join("|", "G", Integer.toString(group.getGroupId()), encode(group.getName()),
                    encode(group.getDescription()), encode(group.getLearningTopic()), encode(group.getOwnerId()),
                    group.getCreationDate().toString(), Integer.toString(group.getMaximumMembers()),
                    group.getStatus().name()));
            for (String memberId : group.getMemberIds())
                lines.add(String.join("|", "M", Integer.toString(group.getGroupId()), encode(memberId)));
        }
        return lines;
    }

    private List<String> serializeGroupLearningSessions(List<GroupLearningSession> sessions) {
        return sessions.stream().map(session -> String.join("|", "C",
                Integer.toString(session.getGroupSessionId()), Integer.toString(session.getGroupId()),
                encode(session.getTitle()), encode(session.getDescription()),
                session.getScheduledDate().toString(), session.getScheduledTime().toString(),
                Integer.toString(session.getDurationMinutes()), encode(session.getOrganizerId()),
                session.getStatus().name())).toList();
    }

    public void saveData(List<Student> students, List<ExchangeRequest> requests) throws IOException {
        if (students == null || requests == null) {
            throw new IllegalArgumentException("Student and request data are required.");
        }
        Files.createDirectories(dataDirectory);
        writeAtomically(dataDirectory.resolve(STUDENTS_FILE), serializeStudents(students));
        writeAtomically(dataDirectory.resolve(REQUESTS_FILE), serializeRequests(requests));
    }

    public void saveData(List<Student> students, List<ExchangeRequest> requests,
                         List<Session> sessions) throws IOException {
        if (sessions == null) throw new IllegalArgumentException("Session data is required.");
        saveData(students, requests);
        Files.createDirectories(dataDirectory);
        writeAtomically(dataDirectory.resolve(SESSIONS_FILE), serializeSessions(sessions));
    }

    /** Persists a student ID change and every file whose records reference that student as one rollback unit. */
    public void saveStudentUpdateData(List<Student> students, List<ExchangeRequest> requests,
                                      List<Session> sessions, List<Rating> ratings,
                                      List<Notification> notifications) throws IOException {
        if (students == null || requests == null || sessions == null || ratings == null || notifications == null)
            throw new IllegalArgumentException("All student update data is required.");
        saveAllData("Student update", students, requests, sessions, ratings, notifications);
    }

    public void saveStudentUpdateData(List<Student> students, List<ExchangeRequest> requests,
                                      List<Session> sessions, List<Rating> ratings,
                                      List<Notification> notifications,
                                      List<LearningRoadmap> roadmaps) throws IOException {
        if (students == null || requests == null || sessions == null || ratings == null
                || notifications == null || roadmaps == null)
            throw new IllegalArgumentException("All student update data is required.");
        saveAllData("Student update", students, requests, sessions, ratings, notifications, roadmaps);
    }

    public void saveStudentUpdateData(List<Student> students, List<ExchangeRequest> requests,
                                      List<Session> sessions, List<Rating> ratings,
                                      List<Notification> notifications, List<LearningRoadmap> roadmaps,
                                      List<RewardTransaction> transactions,
                                      List<AchievementBadge> badges) throws IOException {
        saveAllData("Student update", students, requests, sessions, ratings, notifications,
                roadmaps, transactions, badges);
    }

    public void saveStudentUpdateData(List<Student> students, List<ExchangeRequest> requests,
                                      List<Session> sessions, List<Rating> ratings,
                                      List<Notification> notifications, List<LearningRoadmap> roadmaps,
                                      List<RewardTransaction> transactions, List<AchievementBadge> badges,
                                      List<LearningGroup> groups,
                                      List<GroupLearningSession> groupSessions) throws IOException {
        saveAllData("Student update", students, requests, sessions, ratings, notifications, roadmaps,
                transactions, badges, groups, groupSessions);
    }

    public void saveAllData(List<Student> students, List<ExchangeRequest> requests,
                            List<Session> sessions, List<Rating> ratings,
                            List<Notification> notifications) throws IOException {
        saveAllData("Application data", students, requests, sessions, ratings, notifications);
    }

    public void saveAllData(List<Student> students, List<ExchangeRequest> requests,
                            List<Session> sessions, List<Rating> ratings,
                            List<Notification> notifications, List<LearningRoadmap> roadmaps) throws IOException {
        saveAllData("Application data", students, requests, sessions, ratings, notifications, roadmaps);
    }

    public void saveAllData(List<Student> students, List<ExchangeRequest> requests,
                            List<Session> sessions, List<Rating> ratings,
                            List<Notification> notifications, List<LearningRoadmap> roadmaps,
                            List<RewardTransaction> transactions,
                            List<AchievementBadge> badges) throws IOException {
        saveAllData("Application data", students, requests, sessions, ratings, notifications,
                roadmaps, transactions, badges);
    }

    public void saveAllData(List<Student> students, List<ExchangeRequest> requests,
                            List<Session> sessions, List<Rating> ratings,
                            List<Notification> notifications, List<LearningRoadmap> roadmaps,
                            List<RewardTransaction> transactions, List<AchievementBadge> badges,
                            List<LearningGroup> groups,
                            List<GroupLearningSession> groupSessions) throws IOException {
        saveAllData("Application data", students, requests, sessions, ratings, notifications,
                roadmaps, transactions, badges, groups, groupSessions);
    }

    private void saveAllData(String operation, List<Student> students, List<ExchangeRequest> requests,
                             List<Session> sessions, List<Rating> ratings,
                             List<Notification> notifications) throws IOException {
        saveAllData(operation, students, requests, sessions, ratings, notifications, null);
    }

    private void saveAllData(String operation, List<Student> students, List<ExchangeRequest> requests,
                             List<Session> sessions, List<Rating> ratings,
                             List<Notification> notifications, List<LearningRoadmap> roadmaps) throws IOException {
        saveAllData(operation, students, requests, sessions, ratings, notifications, roadmaps, null, null);
    }

    private void saveAllData(String operation, List<Student> students, List<ExchangeRequest> requests,
                             List<Session> sessions, List<Rating> ratings,
                             List<Notification> notifications, List<LearningRoadmap> roadmaps,
                             List<RewardTransaction> transactions,
                             List<AchievementBadge> badges) throws IOException {
        saveAllData(operation, students, requests, sessions, ratings, notifications, roadmaps,
                transactions, badges, null, null);
    }

    private void saveAllData(String operation, List<Student> students, List<ExchangeRequest> requests,
                             List<Session> sessions, List<Rating> ratings,
                             List<Notification> notifications, List<LearningRoadmap> roadmaps,
                             List<RewardTransaction> transactions, List<AchievementBadge> badges,
                             List<LearningGroup> groups,
                             List<GroupLearningSession> groupSessions) throws IOException {
        if (students == null || requests == null || sessions == null || ratings == null || notifications == null
                || (roadmaps != null && roadmaps.stream().anyMatch(java.util.Objects::isNull))
                || ((transactions == null) != (badges == null))
                || (transactions != null && (transactions.stream().anyMatch(java.util.Objects::isNull)
                || badges.stream().anyMatch(java.util.Objects::isNull)))
                || ((groups == null) != (groupSessions == null))
                || (groups != null && (groups.stream().anyMatch(java.util.Objects::isNull)
                || groupSessions.stream().anyMatch(java.util.Objects::isNull))))
            throw new IllegalArgumentException("All application data is required.");

        Map<String, List<String>> writes = new LinkedHashMap<>();
        writes.put(STUDENTS_FILE, serializeStudents(students));
        writes.put(REQUESTS_FILE, serializeRequests(requests));
        writes.put(SESSIONS_FILE, serializeSessions(sessions));
        writes.put(RATINGS_FILE, serializeRatings(ratings));
        writes.put(NOTIFICATIONS_FILE, serializeNotifications(notifications));
        if (roadmaps != null) writes.put(ROADMAPS_FILE, serializeRoadmaps(roadmaps));
        if (transactions != null) writes.put(REWARDS_FILE, serializeRewards(transactions, badges));
        if (groups != null) {
            writes.put(GROUPS_FILE, serializeLearningGroups(groups));
            writes.put(GROUP_SESSIONS_FILE, serializeGroupLearningSessions(groupSessions));
        }

        Map<String, FileSnapshot> snapshots = new LinkedHashMap<>();
        for (String fileName : writes.keySet()) {
            Path file = dataDirectory.resolve(fileName);
            snapshots.put(fileName, Files.exists(file)
                    ? new FileSnapshot(true, Files.readAllBytes(file))
                    : new FileSnapshot(false, new byte[0]));
        }

        Files.createDirectories(dataDirectory);
        String currentFile = "";
        try {
            for (Map.Entry<String, List<String>> write : writes.entrySet()) {
                currentFile = write.getKey();
                persistenceFailureInjector.beforeWrite(PersistenceStage.forFile(currentFile));
                writeAtomically(dataDirectory.resolve(currentFile), write.getValue());
            }
        } catch (IOException | RuntimeException failure) {
            IOException rollbackFailure = restoreFiles(snapshots);
            if (rollbackFailure != null) {
                rollbackFailure.addSuppressed(failure);
                throw new IOException(operation + " persistence failed at " + currentFile
                        + " and rollback could not fully restore the previous files.", rollbackFailure);
            }
            if (failure instanceof IOException ioFailure) throw ioFailure;
            throw new IOException(operation + " persistence failed at " + currentFile + ".", failure);
        }
    }

    private IOException restoreFiles(Map<String, FileSnapshot> snapshots) {
        IOException failure = null;
        for (Map.Entry<String, FileSnapshot> entry : snapshots.entrySet()) {
            Path target = dataDirectory.resolve(entry.getKey());
            try {
                persistenceFailureInjector.beforeWrite(PersistenceStage.ROLLBACK);
                if (!entry.getValue().existed()) {
                    Files.deleteIfExists(target);
                    continue;
                }
                Path temporaryFile = Files.createTempFile(dataDirectory, target.getFileName().toString(), ".rollback");
                try {
                    Files.write(temporaryFile, entry.getValue().contents());
                    try {
                        Files.move(temporaryFile, target,
                                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException exception) {
                        Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporaryFile);
                }
            } catch (IOException exception) {
                if (failure == null) failure = new IOException("One or more data files could not be restored.");
                failure.addSuppressed(exception);
            }
        }
        return failure;
    }

    private List<SavedSession> loadSessions(List<String> warnings) throws IOException {
        Path file = dataDirectory.resolve(SESSIONS_FILE);
        if (!Files.exists(file)) return List.of();
        List<SavedSession> result = new ArrayList<>();
        Set<Integer> ids = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            try {
                String[] f = lines.get(index).split("\\|", -1);
                if (f.length != 9 || !"L".equals(f[0])) throw new IllegalArgumentException("Invalid session record.");
                int id = parsePositiveInteger(f[1], "session ID");
                if (!ids.add(id)) throw new IllegalArgumentException("Duplicate session ID.");
                result.add(new SavedSession(id, decode(f[2]), decode(f[3]), decode(f[4]),
                        LocalDate.parse(f[5]), LocalTime.parse(f[6]),
                        parsePositiveInteger(f[7], "duration"), Session.Status.valueOf(f[8])));
            } catch (RuntimeException exception) {
                warnings.add(SESSIONS_FILE + " line " + (index + 1) + ": " + exception.getMessage() + " Skipped.");
            }
        }
        return List.copyOf(result);
    }

    private List<String> serializeSessions(List<Session> sessions) {
        List<String> lines = new ArrayList<>();
        for (Session session : sessions) lines.add(String.join("|", "L",
                Integer.toString(session.getSessionId()), encode(session.getTeacher().getId()),
                encode(session.getLearner().getId()), encode(session.getSkill().getName()),
                session.getDate().toString(), session.getStartTime().toString(),
                Integer.toString(session.getDurationMinutes()), session.getStatus().name()));
        return lines;
    }

    private List<Student> loadStudents(List<String> warnings) throws IOException {
        Path file = dataDirectory.resolve(STUDENTS_FILE);
        if (!Files.exists(file)) {
            return List.of();
        }

        List<Student> students = new ArrayList<>();
        Set<String> studentIds = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            try {
                Student student = parseStudent(line);
                String normalizedId = student.getId().toLowerCase(java.util.Locale.ROOT);
                if (!studentIds.add(normalizedId)) {
                    throw new IllegalArgumentException("Duplicate student ID.");
                }
                students.add(student);
            } catch (IllegalArgumentException exception) {
                warnings.add(STUDENTS_FILE + " line " + (index + 1) + ": "
                        + exception.getMessage() + " Skipped.");
            }
        }
        return List.copyOf(students);
    }

    private List<SavedRequest> loadRequests(List<String> warnings) throws IOException {
        Path file = dataDirectory.resolve(REQUESTS_FILE);
        if (!Files.exists(file)) {
            return List.of();
        }

        List<SavedRequest> requests = new ArrayList<>();
        Set<Integer> requestIds = new HashSet<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            try {
                SavedRequest request = parseRequest(line);
                if (!requestIds.add(request.requestId())) {
                    throw new IllegalArgumentException("Duplicate request ID.");
                }
                requests.add(request);
            } catch (IllegalArgumentException exception) {
                warnings.add(REQUESTS_FILE + " line " + (index + 1) + ": "
                        + exception.getMessage() + " Skipped.");
            }
        }
        return List.copyOf(requests);
    }

    private List<String> serializeStudents(List<Student> students) {
        List<String> lines = new ArrayList<>();
        for (Student student : students) {
            List<String> fields = new ArrayList<>();
            fields.add("S");
            fields.add(encode(student.getId()));
            fields.add(encode(student.getName()));
            fields.add(encode(student.getEmail()));
            fields.add(encode(student.getDepartment()));
            fields.add(Integer.toString(student.getYearOfStudy()));
            appendSkills(fields, student.getSkillsToTeach());
            appendSkills(fields, student.getSkillsToLearn());
            lines.add(String.join("|", fields));
        }
        return lines;
    }

    private List<String> serializeRequests(List<ExchangeRequest> requests) {
        List<String> lines = new ArrayList<>();
        for (ExchangeRequest request : requests) {
            lines.add(String.join("|",
                    "R",
                    Integer.toString(request.getRequestId()),
                    encode(request.getRequester().getId()),
                    encode(request.getRecipient().getId()),
                    encode(request.getOfferedSkill().getName()),
                    encode(request.getOfferedSkill().getCategory()),
                    encode(request.getRequestedSkill().getName()),
                    encode(request.getRequestedSkill().getCategory()),
                    request.getStatus().name()));
        }
        return lines;
    }

    private Student parseStudent(String line) {
        String[] fields = line.split("\\|", -1);
        if (fields.length < 8 || !"S".equals(fields[0])) {
            throw new IllegalArgumentException("Invalid student record.");
        }

        int year = parsePositiveInteger(fields[5], "year of study");
        Student student = new Student(
                decode(fields[1]),
                decode(fields[2]),
                decode(fields[3]),
                decode(fields[4]),
                year);

        int index = parseSkills(fields, 6, student, true);
        index = parseSkills(fields, index, student, false);
        if (index != fields.length) {
            throw new IllegalArgumentException("Unexpected data at end of student record.");
        }
        return student;
    }

    private int parseSkills(String[] fields, int start, Student student, boolean teach) {
        if (start >= fields.length) {
            throw new IllegalArgumentException("Missing skill count.");
        }
        int count = parseNonNegativeInteger(fields[start], "skill count");
        if (count > (fields.length - start - 1) / 2) {
            throw new IllegalArgumentException("Skill count does not match record data.");
        }

        int index = start + 1;
        for (int skillIndex = 0; skillIndex < count; skillIndex++) {
            Skill skill = new Skill(decode(fields[index]), decode(fields[index + 1]));
            if (teach) {
                student.addSkillToTeach(skill);
            } else {
                student.addSkillToLearn(skill);
            }
            index += 2;
        }
        return index;
    }

    private SavedRequest parseRequest(String line) {
        String[] fields = line.split("\\|", -1);
        if (fields.length != 9 || !"R".equals(fields[0])) {
            throw new IllegalArgumentException("Invalid exchange request record.");
        }

        int requestId = parsePositiveInteger(fields[1], "request ID");
        ExchangeRequest.Status status;
        try {
            status = ExchangeRequest.Status.valueOf(fields[8]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown exchange request status.");
        }
        return new SavedRequest(
                requestId,
                decode(fields[2]),
                decode(fields[3]),
                decode(fields[4]),
                decode(fields[5]),
                decode(fields[6]),
                decode(fields[7]),
                status);
    }

    private void appendSkills(List<String> fields, List<Skill> skills) {
        fields.add(Integer.toString(skills.size()));
        for (Skill skill : skills) {
            fields.add(encode(skill.getName()));
            fields.add(encode(skill.getCategory()));
        }
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid encoded text field.");
        }
    }

    private int parsePositiveInteger(String value, String fieldName) {
        int number = parseNonNegativeInteger(value, fieldName);
        if (number == 0) {
            throw new IllegalArgumentException(fieldName + " must be positive.");
        }
        return number;
    }

    private int parseNonNegativeInteger(String value, String fieldName) {
        try {
            int number = Integer.parseInt(value);
            if (number < 0) {
                throw new IllegalArgumentException(fieldName + " cannot be negative.");
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid " + fieldName + ".");
        }
    }

    private void writeAtomically(Path target, List<String> lines) throws IOException {
        Path temporaryFile = Files.createTempFile(dataDirectory, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temporaryFile, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporaryFile, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    @FunctionalInterface
    public interface PersistenceFailureInjector {
        void beforeWrite(PersistenceStage stage) throws IOException;
    }

    public enum PersistenceStage {
        STUDENTS, REQUESTS, SESSIONS, RATINGS, NOTIFICATIONS, ROADMAPS, REWARDS,
        GROUPS, GROUP_SESSIONS, ROLLBACK;

        private static PersistenceStage forFile(String fileName) {
            return switch (fileName) {
                case STUDENTS_FILE -> STUDENTS;
                case REQUESTS_FILE -> REQUESTS;
                case SESSIONS_FILE -> SESSIONS;
                case RATINGS_FILE -> RATINGS;
                case NOTIFICATIONS_FILE -> NOTIFICATIONS;
                case ROADMAPS_FILE -> ROADMAPS;
                case REWARDS_FILE -> REWARDS;
                case GROUPS_FILE -> GROUPS;
                case GROUP_SESSIONS_FILE -> GROUP_SESSIONS;
                default -> throw new IllegalArgumentException("Unknown student update data file.");
            };
        }
    }

    private record FileSnapshot(boolean existed, byte[] contents) { }

    public record SavedRequest(
            int requestId,
            String requesterId,
            String recipientId,
            String offeredSkillName,
            String offeredSkillCategory,
            String requestedSkillName,
            String requestedSkillCategory,
            ExchangeRequest.Status status) {
    }

    public record LoadResult(
            List<Student> students, List<SavedRequest> requests, List<SavedSession> sessions, List<String> warnings) {
        public LoadResult {
            students = List.copyOf(students);
            requests = List.copyOf(requests);
            sessions = List.copyOf(sessions);
            warnings = List.copyOf(warnings);
        }
    }

    public record AllData(List<Student> students, List<SavedRequest> requests, List<SavedSession> sessions,
                          List<SavedNotification> notifications, List<SavedRating> ratings,
                          List<SavedRoadmap> roadmaps, List<SavedRewardTransaction> rewardTransactions,
                          List<SavedBadge> badges, List<SavedLearningGroup> groups,
                          List<SavedGroupLearningSession> groupSessions, List<String> warnings) {
        public AllData {
            students = List.copyOf(students);
            requests = List.copyOf(requests);
            sessions = List.copyOf(sessions);
            notifications = List.copyOf(notifications);
            ratings = List.copyOf(ratings);
            roadmaps = List.copyOf(roadmaps);
            rewardTransactions = List.copyOf(rewardTransactions);
            badges = List.copyOf(badges);
            groups = List.copyOf(groups);
            groupSessions = List.copyOf(groupSessions);
            warnings = List.copyOf(warnings);
        }
    }

    public record SavedSession(int sessionId, String teacherId, String learnerId, String skillName,
                               LocalDate date, LocalTime startTime, int durationMinutes,
                               Session.Status status) { }

    public record SavedNotification(int notificationId, String recipientId, String eventType,
                                    String message, Instant createdAt, boolean read) { }

    public record SavedRating(int sessionId, String reviewerId, int score, String feedback, Instant createdAt) { }

    public record SavedRoadmap(int roadmapId, String studentId, String title, String targetSkill,
                               String description, LocalDate creationDate, LocalDate targetCompletionDate,
                               List<SavedRoadmapMilestone> milestones) {
        public SavedRoadmap {
            milestones = List.copyOf(milestones);
        }
    }

    public record SavedRoadmapMilestone(int milestoneId, String title, String description,
                                        LocalDate dueDate, boolean completed) { }

    public record SavedRewardTransaction(int transactionId, String studentId, String eventReference,
                                         String description, int points, Instant createdAt) { }

    public record SavedBadge(String badgeId, String studentId, String badgeName, LocalDate dateEarned) { }

    public record SavedLearningGroup(int groupId, String name, String description, String topic, String ownerId,
                                     LocalDate creationDate, int maximumMembers, LearningGroup.Status status,
                                     List<String> memberIds) {
        public SavedLearningGroup {
            memberIds = List.copyOf(memberIds);
        }
    }

    public record SavedGroupLearningSession(int sessionId, int groupId, String title, String description,
                                            LocalDate date, LocalTime time, int durationMinutes,
                                            String organizerId, GroupLearningSession.Status status) { }

    private record LoadedRewards(List<SavedRewardTransaction> transactions, List<SavedBadge> badges) { }

    private static final class SavedLearningGroupBuilder {
        private final int groupId;
        private final String name;
        private final String description;
        private final String topic;
        private final String ownerId;
        private final LocalDate creationDate;
        private final int maximumMembers;
        private final LearningGroup.Status status;
        private final List<String> memberIds = new ArrayList<>();

        private SavedLearningGroupBuilder(int groupId, String name, String description, String topic,
                                          String ownerId, LocalDate creationDate, int maximumMembers,
                                          LearningGroup.Status status) {
            this.groupId = groupId;
            this.name = name;
            this.description = description;
            this.topic = topic;
            this.ownerId = ownerId;
            this.creationDate = creationDate;
            this.maximumMembers = maximumMembers;
            this.status = status;
        }

        private SavedLearningGroup build() {
            return new SavedLearningGroup(groupId, name, description, topic, ownerId,
                    creationDate, maximumMembers, status, memberIds);
        }
    }

    private static final class SavedRoadmapBuilder {
        private final int roadmapId;
        private final String studentId;
        private final String title;
        private final String targetSkill;
        private final String description;
        private final LocalDate creationDate;
        private final LocalDate targetCompletionDate;
        private final List<SavedRoadmapMilestone> milestones = new ArrayList<>();

        private SavedRoadmapBuilder(int roadmapId, String studentId, String title, String targetSkill,
                                    String description, LocalDate creationDate, LocalDate targetCompletionDate) {
            this.roadmapId = roadmapId;
            this.studentId = studentId;
            this.title = title;
            this.targetSkill = targetSkill;
            this.description = description;
            this.creationDate = creationDate;
            this.targetCompletionDate = targetCompletionDate;
        }

        private SavedRoadmap build() {
            return new SavedRoadmap(roadmapId, studentId, title, targetSkill, description,
                    creationDate, targetCompletionDate, milestones);
        }
    }
}
