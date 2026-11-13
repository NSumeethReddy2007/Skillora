import model.AchievementBadge;
import model.LearningRoadmap;
import model.RoadmapMilestone;
import model.Session;
import model.Skill;
import model.Student;
import service.LearningRoadmapManager;
import service.RewardManager;
import service.SessionManager;
import service.StudentManager;
import storage.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class RewardManagerChecks {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("skillora-reward-check-");
        try {
            verifyAwardsBadgesLeaderboardAndHistory(directory);
            verifyCoordinatedPersistenceAndRollback(directory);
            System.out.println("Reward manager checks passed.");
        } finally {
            deleteTemporaryDirectory(directory);
        }
    }

    private static void verifyAwardsBadgesLeaderboardAndHistory(Path directory) throws Exception {
        Managers managers = createManagers();
        Session scheduled = managers.sessions().createSession("REWARD-A", "REWARD-B", "Java",
                LocalDate.now().plusDays(2), LocalTime.of(10, 0), 60);
        expectStateFailure(() -> managers.rewards().awardCompletedSession(scheduled));
        managers.sessions().cancelSession(scheduled.getSessionId(), "REWARD-A");
        expectStateFailure(() -> managers.rewards().awardCompletedSession(scheduled));
        require(managers.rewards().getTotalPoints("REWARD-A") == 0,
                "Scheduled or cancelled sessions must not award points.");

        for (int index = 0; index < 10; index++) {
            Session session = managers.sessions().createSession("REWARD-A", "REWARD-B", "Java",
                    LocalDate.now().plusDays(3 + index), LocalTime.of(10, 0), 60);
            managers.sessions().completeSession(session.getSessionId(), "REWARD-B");
            require(managers.rewards().awardCompletedSession(session) == 2,
                    "A completed session must award both participants.");
            require(managers.rewards().awardCompletedSession(session) == 0,
                    "The same session must not award duplicate transactions.");
        }
        require(managers.rewards().getTotalPoints("REWARD-A") == 100
                        && managers.rewards().getTotalPoints("REWARD-B") == 100,
                "Each participant must receive ten points for each completed session.");
        List<RewardManager.LeaderboardEntry> tied = managers.rewards().getLeaderboard();
        require(tied.size() == 3 && tied.get(0).rank() == 1 && tied.get(1).rank() == 1
                        && tied.get(2).rank() == 3 && tied.get(2).totalPoints() == 0,
                "Equal point totals must share a deterministic rank, and zero-point students must appear.");
        require(tied.get(0).student().getId().equals("REWARD-A")
                        && tied.get(1).student().getId().equals("REWARD-B"),
                "Ties must be ordered deterministically by student name and ID.");
        require(managers.rewards().getBadgesForStudent("REWARD-A").size() == 3,
                "One, five, and ten completed sessions must automatically award the session badges.");

        LearningRoadmap roadmap = managers.roadmaps().createRoadmap("REWARD-A", "Java roadmap",
                "Java", "Practice Java.", LocalDate.now().plusDays(30));
        RoadmapMilestone first = managers.roadmaps().addMilestone(roadmap.getRoadmapId(),
                "Syntax", "Learn syntax.", LocalDate.now().plusDays(5));
        RoadmapMilestone second = managers.roadmaps().addMilestone(roadmap.getRoadmapId(),
                "Classes", "Learn classes.", LocalDate.now().plusDays(10));
        managers.roadmaps().setMilestoneCompleted(roadmap.getRoadmapId(), first.getMilestoneId(), true);
        require(managers.rewards().awardCompletedMilestone(roadmap.getRoadmapId(), first.getMilestoneId()) == 1,
                "A completed milestone must award five points.");
        require(managers.rewards().awardCompletedMilestone(roadmap.getRoadmapId(), first.getMilestoneId()) == 0,
                "A completed milestone must not award duplicate points.");
        require(managers.rewards().getTotalPoints("REWARD-A") == 105
                        && hasBadge(managers.rewards(), "REWARD-A", "FIRST_MILESTONE"),
                "First milestone completion must award points and the First Milestone badge.");

        managers.roadmaps().setMilestoneCompleted(roadmap.getRoadmapId(), second.getMilestoneId(), true);
        require(managers.rewards().awardCompletedMilestone(roadmap.getRoadmapId(), second.getMilestoneId()) == 2,
                "The final milestone must also award the one-time roadmap completion bonus.");
        require(managers.rewards().awardCompletedMilestone(roadmap.getRoadmapId(), second.getMilestoneId()) == 0,
                "The roadmap completion bonus must not be repeated.");
        require(managers.rewards().getTotalPoints("REWARD-A") == 130
                        && hasBadge(managers.rewards(), "REWARD-A", "ROADMAP_CHAMPION"),
                "Roadmap completion must award 20 bonus points and the Roadmap Champion badge.");
        require(managers.rewards().getBadgesForStudent("REWARD-A").size() == 5,
                "Each of the five badge definitions must be awarded at most once.");
        require(managers.rewards().getTransactionsForStudent("REWARD-A").size() == 13,
                "Reward history must include ten sessions, two milestones, and one roadmap bonus.");
        require(managers.rewards().synchronizeCompletedActivities() == 0,
                "Synchronizing completed activities must not award duplicate transactions.");
        Student original = managers.students().findStudentById("REWARD-A").orElseThrow();
        Student renamed = new Student("REWARD-A-RENAMED", original.getName(), original.getEmail(),
                original.getDepartment(), original.getYearOfStudy());
        original.getSkillsToTeach().forEach(renamed::addSkillToTeach);
        original.getSkillsToLearn().forEach(renamed::addSkillToLearn);
        managers.students().updateStudent("REWARD-A", renamed);
        managers.rewards().updateStudentId("REWARD-A", renamed.getId());
        require(managers.rewards().hasRewardsForStudent(renamed.getId())
                        && managers.rewards().getTotalPoints(renamed.getId()) == 130
                        && hasBadge(managers.rewards(), renamed.getId(), "ROADMAP_CHAMPION"),
                "Student ID updates must preserve reward history and badge references.");
    }

    private static void verifyCoordinatedPersistenceAndRollback(Path directory) throws Exception {
        Managers managers = createManagers();
        LearningRoadmap roadmap = managers.roadmaps().createRoadmap("REWARD-A", "Persisted roadmap",
                "Java", "Persistence coverage.", LocalDate.now().plusDays(30));
        RoadmapMilestone milestone = managers.roadmaps().addMilestone(roadmap.getRoadmapId(),
                "Persisted milestone", "Saved milestone.", LocalDate.now().plusDays(7));
        managers.roadmaps().setMilestoneCompleted(roadmap.getRoadmapId(), milestone.getMilestoneId(), true);
        managers.rewards().awardCompletedMilestone(roadmap.getRoadmapId(), milestone.getMilestoneId());
        FileManager files = new FileManager(directory);
        saveAll(files, managers);
        byte[] rewardsBeforeLegacySave = Files.readAllBytes(directory.resolve("rewards.txt"));
        files.saveAllData(managers.students().getAllStudents(), List.of(), List.of(), List.of(), List.of(),
                managers.roadmaps().getAllRoadmaps());
        require(Arrays.equals(rewardsBeforeLegacySave, Files.readAllBytes(directory.resolve("rewards.txt"))),
                "The existing six-record persistence overload must preserve reward data.");

        Managers restored = restoreManagers(new FileManager(directory).loadAllData());
        require(restored.rewards().getTotalPoints("REWARD-A") == 25
                        && restored.rewards().getTransactionsForStudent("REWARD-A").size() == 2
                        && hasBadge(restored.rewards(), "REWARD-A", "FIRST_MILESTONE")
                        && hasBadge(restored.rewards(), "REWARD-A", "ROADMAP_CHAMPION"),
                "Reward transactions and badges must survive application restart.");
        require(restored.rewards().synchronizeCompletedActivities() == 0,
                "Restart reconciliation must not duplicate persisted rewards.");
        require(restored.roadmaps().getRoadmap(roadmap.getRoadmapId())
                        .getMilestone(milestone.getMilestoneId()).isCompleted(),
                "Reward persistence must not disrupt roadmap persistence.");

        Map<String, byte[]> originals = List.of("students.txt", "requests.txt", "sessions.txt",
                        "ratings.txt", "notifications.txt", "roadmaps.txt", "rewards.txt").stream()
                .collect(Collectors.toMap(name -> name, name -> readBytes(directory.resolve(name))));
        AtomicBoolean failOnce = new AtomicBoolean(true);
        FileManager failingFiles = new FileManager(directory, stage -> {
            if (stage == FileManager.PersistenceStage.REWARDS && failOnce.compareAndSet(true, false))
                throw new IOException("Injected reward persistence failure.");
        });
        try {
            saveAll(failingFiles, restored);
            throw new AssertionError("Expected coordinated save failure at the reward file.");
        } catch (IOException expected) {
            require(expected.getMessage().contains("Injected reward persistence failure."),
                    "The save failure should be reported explicitly.");
        }
        for (Map.Entry<String, byte[]> original : originals.entrySet()) {
            require(Arrays.equals(original.getValue(), readBytes(directory.resolve(original.getKey()))),
                    "Coordinated rollback must restore " + original.getKey() + ".");
        }
    }

    private static void saveAll(FileManager files, Managers managers) throws IOException {
        files.saveAllData(managers.students().getAllStudents(), List.of(), managers.sessions().getAllSessions(),
                List.of(), List.of(), managers.roadmaps().getAllRoadmaps(),
                managers.rewards().getAllTransactions(), managers.rewards().getAllBadges());
    }

    private static Managers restoreManagers(FileManager.AllData data) {
        StudentManager students = new StudentManager();
        students.loadStudents(data.students());
        SessionManager sessions = new SessionManager(students);
        for (FileManager.SavedSession session : data.sessions()) {
            sessions.restoreSession(session.sessionId(), session.teacherId(), session.learnerId(),
                    session.skillName(), session.date(), session.startTime(), session.durationMinutes(),
                    session.status());
        }
        LearningRoadmapManager roadmaps = new LearningRoadmapManager(students);
        for (FileManager.SavedRoadmap saved : data.roadmaps()) {
            List<LearningRoadmapManager.SavedMilestone> milestones = saved.milestones().stream()
                    .map(item -> new LearningRoadmapManager.SavedMilestone(item.milestoneId(), item.title(),
                            item.description(), item.dueDate(), item.completed())).toList();
            roadmaps.restoreRoadmap(saved.roadmapId(), saved.studentId(), saved.title(), saved.targetSkill(),
                    saved.description(), saved.creationDate(), saved.targetCompletionDate(), milestones);
        }
        RewardManager rewards = new RewardManager(students, sessions, roadmaps);
        for (FileManager.SavedRewardTransaction saved : data.rewardTransactions()) {
            rewards.restoreTransaction(saved.transactionId(), saved.studentId(), saved.eventReference(),
                    saved.description(), saved.points(), saved.createdAt());
        }
        for (FileManager.SavedBadge saved : data.badges()) {
            rewards.restoreBadge(saved.badgeId(), saved.studentId(), saved.badgeName(), saved.dateEarned());
        }
        return new Managers(students, sessions, roadmaps, rewards);
    }

    private static Managers createManagers() {
        StudentManager students = new StudentManager();
        Student first = new Student("REWARD-A", "Alice", "alice@example.test", "Computing", 2);
        first.addSkillToTeach(new Skill("Java", "Programming"));
        Student second = new Student("REWARD-B", "Bob", "bob@example.test", "Computing", 2);
        second.addSkillToLearn(new Skill("Java", "Programming"));
        Student zero = new Student("REWARD-C", "Carol", "carol@example.test", "Computing", 1);
        students.registerStudent(first);
        students.registerStudent(second);
        students.registerStudent(zero);
        SessionManager sessions = new SessionManager(students);
        LearningRoadmapManager roadmaps = new LearningRoadmapManager(students);
        return new Managers(students, sessions, roadmaps, new RewardManager(students, sessions, roadmaps));
    }

    private static boolean hasBadge(RewardManager manager, String studentId, String badgeId) {
        return manager.getBadgesForStudent(studentId).stream()
                .anyMatch(badge -> badge.getBadgeId().equals(badgeId));
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read " + file, exception);
        }
    }

    private static void expectStateFailure(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected invalid reward activity to be rejected.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTemporaryDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    private record Managers(StudentManager students, SessionManager sessions,
                            LearningRoadmapManager roadmaps, RewardManager rewards) { }
}
