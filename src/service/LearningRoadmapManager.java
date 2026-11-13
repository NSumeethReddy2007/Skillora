package service;

import model.LearningRoadmap;
import model.RoadmapMilestone;
import model.Student;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

public final class LearningRoadmapManager {
    private final StudentManager studentManager;
    private final ArrayList<LearningRoadmap> roadmaps = new ArrayList<>();
    private int nextRoadmapId = 1;

    public LearningRoadmapManager(StudentManager studentManager) {
        if (studentManager == null) throw new IllegalArgumentException("Student manager is required.");
        this.studentManager = studentManager;
    }

    public LearningRoadmap createRoadmap(String studentId, String title, String targetSkill,
                                         String description, LocalDate targetCompletionDate) {
        Student student = findStudent(studentId);
        LearningRoadmap roadmap = new LearningRoadmap(nextRoadmapId, student.getId(), title,
                targetSkill, description, LocalDate.now(), targetCompletionDate);
        roadmaps.add(roadmap);
        nextRoadmapId++;
        return roadmap;
    }

    public List<LearningRoadmap> getAllRoadmaps() {
        return roadmaps.stream().sorted(Comparator.comparing(LearningRoadmap::getRoadmapId)).toList();
    }

    public List<LearningRoadmap> getRoadmapsForStudent(String studentId) {
        Student student = findStudent(studentId);
        return getAllRoadmaps().stream()
                .filter(roadmap -> roadmap.getStudentId().equalsIgnoreCase(student.getId()))
                .toList();
    }

    public LearningRoadmap getRoadmap(int roadmapId) {
        return roadmaps.stream().filter(roadmap -> roadmap.getRoadmapId() == roadmapId)
                .findFirst().orElseThrow(() -> new NoSuchElementException("Learning roadmap was not found."));
    }

    public void updateRoadmap(int roadmapId, String studentId, String title, String targetSkill,
                              String description, LocalDate targetCompletionDate) {
        Student student = findStudent(studentId);
        getRoadmap(roadmapId).update(student.getId(), title, targetSkill, description, targetCompletionDate);
    }

    public RoadmapMilestone addMilestone(int roadmapId, String title, String description, LocalDate dueDate) {
        return getRoadmap(roadmapId).addMilestone(title, description, dueDate);
    }

    public void updateMilestone(int roadmapId, int milestoneId, String title,
                                String description, LocalDate dueDate) {
        LearningRoadmap roadmap = getRoadmap(roadmapId);
        if (dueDate == null || dueDate.isBefore(roadmap.getCreationDate())
                || dueDate.isAfter(roadmap.getTargetCompletionDate())) {
            throw new IllegalArgumentException(
                    "Milestone due date must be between the roadmap creation and target completion dates.");
        }
        String validTitle = title == null ? "" : title.trim();
        if (roadmap.getMilestones().stream().anyMatch(milestone ->
                milestone.getMilestoneId() != milestoneId
                        && milestone.getTitle().equalsIgnoreCase(validTitle)))
            throw new IllegalArgumentException("A milestone with this title already exists in the roadmap.");
        roadmap.getMilestone(milestoneId).update(title, description, dueDate);
    }

    public void setMilestoneCompleted(int roadmapId, int milestoneId, boolean completed) {
        getRoadmap(roadmapId).getMilestone(milestoneId).setCompleted(completed);
    }

    public boolean canDeleteRoadmap(int roadmapId) {
        return getRoadmap(roadmapId).getStatus() != LearningRoadmap.Status.COMPLETED;
    }

    public void deleteRoadmap(int roadmapId) {
        LearningRoadmap roadmap = getRoadmap(roadmapId);
        if (!canDeleteRoadmap(roadmapId))
            throw new IllegalStateException("Completed roadmaps cannot be deleted.");
        roadmaps.remove(roadmap);
    }

    public boolean hasRoadmapsForStudent(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        return roadmaps.stream().anyMatch(roadmap ->
                roadmap.getStudentId().equalsIgnoreCase(studentId.trim()));
    }

    public void updateStudentId(String oldStudentId, String newStudentId) {
        Student updatedStudent = findStudent(newStudentId);
        if (oldStudentId == null || oldStudentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        roadmaps.stream()
                .filter(roadmap -> roadmap.getStudentId().equalsIgnoreCase(oldStudentId.trim()))
                .forEach(roadmap -> roadmap.reassignStudent(updatedStudent.getId()));
    }

    public void restoreRoadmap(int roadmapId, String studentId, String title, String targetSkill,
                               String description, LocalDate creationDate, LocalDate targetCompletionDate,
                               List<SavedMilestone> milestones) {
        if (roadmapId < 1) throw new IllegalArgumentException("Roadmap ID must be positive.");
        if (roadmaps.stream().anyMatch(item -> item.getRoadmapId() == roadmapId))
            throw new IllegalArgumentException("Roadmap data contains a duplicate roadmap ID.");
        Student student = findStudent(studentId);
        LearningRoadmap roadmap = new LearningRoadmap(roadmapId, student.getId(), title, targetSkill,
                description, creationDate, targetCompletionDate);
        for (SavedMilestone milestone : milestones) {
            roadmap.restoreMilestone(milestone.milestoneId(), milestone.title(), milestone.description(),
                    milestone.dueDate(), milestone.completed());
        }
        roadmaps.add(roadmap);
        nextRoadmapId = Math.max(nextRoadmapId, roadmapId + 1);
    }

    private Student findStudent(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        return studentManager.findStudentById(studentId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Student profile was not found."));
    }

    public record SavedMilestone(int milestoneId, String title, String description,
                                 LocalDate dueDate, boolean completed) { }
}
