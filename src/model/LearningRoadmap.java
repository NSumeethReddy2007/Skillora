package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LearningRoadmap {
    public enum Status { NOT_STARTED, IN_PROGRESS, COMPLETED }

    private final int roadmapId;
    private String studentId;
    private String title;
    private String targetSkill;
    private String description;
    private final LocalDate creationDate;
    private LocalDate targetCompletionDate;
    private final ArrayList<RoadmapMilestone> milestones = new ArrayList<>();
    private int nextMilestoneId = 1;

    public LearningRoadmap(int roadmapId, String studentId, String title, String targetSkill,
                           String description, LocalDate creationDate, LocalDate targetCompletionDate) {
        if (roadmapId < 1) throw new IllegalArgumentException("Roadmap ID must be positive.");
        this.roadmapId = roadmapId;
        this.creationDate = requireDate(creationDate, "Creation date");
        update(studentId, title, targetSkill, description, targetCompletionDate);
    }

    public int getRoadmapId() { return roadmapId; }
    public String getStudentId() { return studentId; }
    public String getTitle() { return title; }
    public String getTargetSkill() { return targetSkill; }
    public String getDescription() { return description; }
    public LocalDate getCreationDate() { return creationDate; }
    public LocalDate getTargetCompletionDate() { return targetCompletionDate; }

    public List<RoadmapMilestone> getMilestones() {
        return milestones.stream().sorted(Comparator.comparingInt(RoadmapMilestone::getMilestoneId)).toList();
    }

    public void update(String studentId, String title, String targetSkill,
                       String description, LocalDate targetCompletionDate) {
        String validStudentId = requireText(studentId, "Student ID");
        String validTitle = requireText(title, "Roadmap title");
        String validSkill = requireText(targetSkill, "Target skill");
        String validDescription = requireText(description, "Roadmap description");
        LocalDate validTarget = requireDate(targetCompletionDate, "Target completion date");
        if (!validTarget.isAfter(creationDate))
            throw new IllegalArgumentException("Target completion date must be after the creation date.");
        if (milestones.stream().anyMatch(milestone -> milestone.getDueDate().isAfter(validTarget)))
            throw new IllegalArgumentException("Target completion date cannot precede a milestone due date.");
        this.studentId = validStudentId;
        this.title = validTitle;
        this.targetSkill = validSkill;
        this.description = validDescription;
        this.targetCompletionDate = validTarget;
    }

    public RoadmapMilestone addMilestone(String title, String description, LocalDate dueDate) {
        validateMilestoneDueDate(dueDate);
        if (title == null || title.isBlank())
            throw new IllegalArgumentException("Milestone title is required.");
        if (milestones.stream().anyMatch(item -> item.getTitle().equalsIgnoreCase(title.trim())))
            throw new IllegalArgumentException("A milestone with this title already exists in the roadmap.");
        RoadmapMilestone milestone = new RoadmapMilestone(nextMilestoneId++, title, description, dueDate, false);
        milestones.add(milestone);
        return milestone;
    }

    public void restoreMilestone(int milestoneId, String title, String description,
                                 LocalDate dueDate, boolean completed) {
        if (milestoneId < 1) throw new IllegalArgumentException("Milestone ID must be positive.");
        if (milestones.stream().anyMatch(item -> item.getMilestoneId() == milestoneId))
            throw new IllegalArgumentException("Roadmap contains a duplicate milestone ID.");
        validateMilestoneDueDate(dueDate);
        if (milestones.stream().anyMatch(item -> item.getTitle().equalsIgnoreCase(title.trim())))
            throw new IllegalArgumentException("Roadmap contains a duplicate milestone title.");
        milestones.add(new RoadmapMilestone(milestoneId, title, description, dueDate, completed));
        nextMilestoneId = Math.max(nextMilestoneId, milestoneId + 1);
    }

    public RoadmapMilestone getMilestone(int milestoneId) {
        return milestones.stream().filter(item -> item.getMilestoneId() == milestoneId)
                .findFirst().orElseThrow(() -> new java.util.NoSuchElementException("Milestone was not found."));
    }

    public int getProgressPercent() {
        if (milestones.isEmpty()) return 0;
        long completed = milestones.stream().filter(RoadmapMilestone::isCompleted).count();
        return (int) (completed * 100 / milestones.size());
    }

    public Status getStatus() {
        if (milestones.isEmpty() || milestones.stream().noneMatch(RoadmapMilestone::isCompleted))
            return Status.NOT_STARTED;
        return milestones.stream().allMatch(RoadmapMilestone::isCompleted)
                ? Status.COMPLETED : Status.IN_PROGRESS;
    }

    public void reassignStudent(String studentId) {
        this.studentId = requireText(studentId, "Student ID");
    }

    private void validateMilestoneDueDate(LocalDate dueDate) {
        LocalDate validDueDate = requireDate(dueDate, "Milestone due date");
        if (validDueDate.isBefore(creationDate) || validDueDate.isAfter(targetCompletionDate))
            throw new IllegalArgumentException(
                    "Milestone due date must be between the roadmap creation and target completion dates.");
    }

    private static LocalDate requireDate(LocalDate value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required.");
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }
}
