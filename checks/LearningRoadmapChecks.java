import model.LearningRoadmap;
import model.RoadmapMilestone;
import model.Skill;
import model.Student;
import service.LearningRoadmapManager;
import service.StudentManager;
import storage.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LearningRoadmapChecks {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("skillora-learning-roadmap-check-");
        try {
            verifyRoadmapAndMilestoneLifecycle(directory);
            verifyCoordinatedPersistenceAndLegacyCompatibility(directory);
            verifyRollback(directory);
            System.out.println("Learning roadmap checks passed.");
        } finally {
            deleteTemporaryDirectory(directory);
        }
    }

    private static void verifyRoadmapAndMilestoneLifecycle(Path directory) throws Exception {
        StudentManager students = createStudents();
        LearningRoadmapManager manager = new LearningRoadmapManager(students);
        LocalDate target = LocalDate.now().plusDays(30);
        LearningRoadmap roadmap = manager.createRoadmap("ROADMAP-STUDENT", "Java foundations",
                "Java", "Build confidence with Java fundamentals.", target);
        require(roadmap.getProgressPercent() == 0
                        && roadmap.getStatus() == LearningRoadmap.Status.NOT_STARTED,
                "A roadmap without completed milestones must start at zero progress.");
        expectArgumentFailure(() -> manager.createRoadmap("missing", "Invalid", "Java",
                "Missing student", target));
        expectArgumentFailure(() -> manager.createRoadmap("ROADMAP-STUDENT", "Invalid", "Java",
                "Invalid target", LocalDate.now()));

        RoadmapMilestone first = manager.addMilestone(roadmap.getRoadmapId(), "Variables",
                "Practice variables and types.", LocalDate.now().plusDays(5));
        RoadmapMilestone second = manager.addMilestone(roadmap.getRoadmapId(), "Methods",
                "Write and call methods.", LocalDate.now().plusDays(10));
        expectArgumentFailure(() -> manager.addMilestone(roadmap.getRoadmapId(), "Variables",
                "Duplicate title.", LocalDate.now().plusDays(6)));
        expectArgumentFailure(() -> manager.addMilestone(roadmap.getRoadmapId(), "Too late",
                "Outside the roadmap.", target.plusDays(1)));
        manager.setMilestoneCompleted(roadmap.getRoadmapId(), first.getMilestoneId(), true);
        require(roadmap.getProgressPercent() == 50
                        && roadmap.getStatus() == LearningRoadmap.Status.IN_PROGRESS,
                "Completing one of two milestones must set progress to 50% and status to in progress.");

        manager.updateRoadmap(roadmap.getRoadmapId(), "ROADMAP-STUDENT", "Java foundations - revised",
                "Java", "Revised learning goals.", target.plusDays(2));
        manager.updateMilestone(roadmap.getRoadmapId(), second.getMilestoneId(), "Methods and classes",
                "Practice methods and basic classes.", LocalDate.now().plusDays(12));
        require(roadmap.getTitle().endsWith("revised")
                        && second.getTitle().equals("Methods and classes"),
                "Roadmap and milestone edits were not applied.");
        expectArgumentFailure(() -> manager.updateRoadmap(roadmap.getRoadmapId(), "ROADMAP-STUDENT",
                "Invalid target", "Java", "Would precede milestone.", LocalDate.now().plusDays(6)));

        manager.setMilestoneCompleted(roadmap.getRoadmapId(), second.getMilestoneId(), true);
        require(roadmap.getProgressPercent() == 100
                        && roadmap.getStatus() == LearningRoadmap.Status.COMPLETED
                        && !manager.canDeleteRoadmap(roadmap.getRoadmapId()),
                "All milestones completed must produce a protected completed roadmap.");
        expectStateFailure(() -> manager.deleteRoadmap(roadmap.getRoadmapId()));
        manager.setMilestoneCompleted(roadmap.getRoadmapId(), second.getMilestoneId(), false);
        Student existingStudent = students.findStudentById("ROADMAP-STUDENT").orElseThrow();
        Student updatedStudent = new Student("ROADMAP-STUDENT-RENAMED", existingStudent.getName(),
                existingStudent.getEmail(), existingStudent.getDepartment(), existingStudent.getYearOfStudy());
        existingStudent.getSkillsToTeach().forEach(updatedStudent::addSkillToTeach);
        existingStudent.getSkillsToLearn().forEach(updatedStudent::addSkillToLearn);
        students.updateStudent("ROADMAP-STUDENT", updatedStudent);
        manager.updateStudentId("ROADMAP-STUDENT", "ROADMAP-STUDENT-RENAMED");
        require(roadmap.getStudentId().equals("ROADMAP-STUDENT-RENAMED")
                        && manager.hasRoadmapsForStudent("ROADMAP-STUDENT-RENAMED"),
                "Student ID updates must keep roadmap references valid.");
        manager.deleteRoadmap(roadmap.getRoadmapId());
        require(manager.getAllRoadmaps().isEmpty(), "An incomplete roadmap should be deletable.");
    }

    private static void verifyCoordinatedPersistenceAndLegacyCompatibility(Path directory) throws Exception {
        StudentManager students = createStudents();
        LearningRoadmapManager manager = new LearningRoadmapManager(students);
        LearningRoadmap roadmap = manager.createRoadmap("ROADMAP-STUDENT", "Persisted roadmap",
                "Java", "Survives application restart.", LocalDate.now().plusDays(30));
        RoadmapMilestone milestone = manager.addMilestone(roadmap.getRoadmapId(), "First milestone",
                "Saved milestone description.", LocalDate.now().plusDays(7));
        manager.setMilestoneCompleted(roadmap.getRoadmapId(), milestone.getMilestoneId(), true);
        FileManager files = new FileManager(directory);
        files.saveAllData(students.getAllStudents(), List.of(), List.of(), List.of(), List.of(),
                manager.getAllRoadmaps());
        byte[] roadmapFileBeforeLegacySave = Files.readAllBytes(directory.resolve("roadmaps.txt"));

        files.saveAllData(students.getAllStudents(), List.of(), List.of(), List.of(), List.of());
        require(Arrays.equals(roadmapFileBeforeLegacySave,
                        Files.readAllBytes(directory.resolve("roadmaps.txt"))),
                "Legacy five-record save unexpectedly overwrote roadmap data.");

        FileManager.AllData loaded = files.loadAllData();
        require(loaded.roadmaps().size() == 1, "Roadmap data did not survive load.");
        StudentManager restoredStudents = new StudentManager();
        restoredStudents.loadStudents(loaded.students());
        LearningRoadmapManager restoredManager = new LearningRoadmapManager(restoredStudents);
        for (FileManager.SavedRoadmap saved : loaded.roadmaps()) {
            List<LearningRoadmapManager.SavedMilestone> savedMilestones = saved.milestones().stream()
                    .map(item -> new LearningRoadmapManager.SavedMilestone(item.milestoneId(), item.title(),
                            item.description(), item.dueDate(), item.completed()))
                    .toList();
            restoredManager.restoreRoadmap(saved.roadmapId(), saved.studentId(), saved.title(),
                    saved.targetSkill(), saved.description(), saved.creationDate(),
                    saved.targetCompletionDate(), savedMilestones);
        }
        LearningRoadmap restored = restoredManager.getRoadmap(roadmap.getRoadmapId());
        require(restored.getStudentId().equals("ROADMAP-STUDENT")
                        && restored.getProgressPercent() == 100
                        && restored.getStatus() == LearningRoadmap.Status.COMPLETED
                        && restored.getMilestones().getFirst().getDescription()
                        .equals("Saved milestone description."),
                "Roadmap metadata, completion state, or progress did not survive restart.");
    }

    private static void verifyRollback(Path directory) throws Exception {
        FileManager files = new FileManager(directory);
        FileManager.AllData loaded = files.loadAllData();
        StudentManager students = new StudentManager();
        students.loadStudents(loaded.students());
        LearningRoadmapManager roadmaps = new LearningRoadmapManager(students);
        for (FileManager.SavedRoadmap saved : loaded.roadmaps()) {
            List<LearningRoadmapManager.SavedMilestone> milestones = saved.milestones().stream()
                    .map(item -> new LearningRoadmapManager.SavedMilestone(item.milestoneId(), item.title(),
                            item.description(), item.dueDate(), item.completed()))
                    .toList();
            roadmaps.restoreRoadmap(saved.roadmapId(), saved.studentId(), saved.title(), saved.targetSkill(),
                    saved.description(), saved.creationDate(), saved.targetCompletionDate(), milestones);
        }
        MapSnapshot before = snapshot(directory);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        FileManager failingFiles = new FileManager(directory, stage -> {
            if (stage == FileManager.PersistenceStage.ROADMAPS && failOnce.compareAndSet(true, false))
                throw new IOException("Injected roadmap write failure.");
        });
        roadmaps.createRoadmap("ROADMAP-STUDENT", "Rollback probe", "Java",
                "This should not be persisted.", LocalDate.now().plusDays(40));
        try {
            failingFiles.saveAllData(students.getAllStudents(), List.of(), List.of(), List.of(), List.of(),
                    roadmaps.getAllRoadmaps());
            throw new AssertionError("Expected coordinated roadmap persistence to fail.");
        } catch (IOException expected) {
            require(expected.getMessage().contains("Injected roadmap write failure."),
                    "The roadmap persistence failure cause was not reported.");
        }
        MapSnapshot after = snapshot(directory);
        require(before.contents().keySet().equals(after.contents().keySet()),
                "Failed roadmap save changed the set of persisted files.");
        for (String file : before.contents().keySet()) {
            require(Arrays.equals(before.contents().get(file), after.contents().get(file)),
                    file + " changed despite failed coordinated roadmap persistence.");
        }
    }

    private static StudentManager createStudents() {
        StudentManager students = new StudentManager();
        Student student = new Student("ROADMAP-STUDENT", "Roadmap Student",
                "roadmap@example.edu", "Computing", 2);
        student.addSkillToLearn(new Skill("Java", "Technology"));
        student.addSkillToTeach(new Skill("Drawing", "Creative"));
        students.registerStudent(student);
        return students;
    }

    private static MapSnapshot snapshot(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            var contents = files.filter(Files::isRegularFile).collect(
                    java.util.stream.Collectors.toMap(path -> path.getFileName().toString(), path -> {
                        try {
                            return Files.readAllBytes(path);
                        } catch (IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    }));
            return new MapSnapshot(contents);
        }
    }

    private static void deleteTemporaryDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void expectArgumentFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected argument validation to fail.");
    }

    private static void expectStateFailure(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected state validation to fail.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record MapSnapshot(java.util.Map<String, byte[]> contents) { }
}
