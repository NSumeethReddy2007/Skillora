package model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class LearningGroup {
    public enum Status { ACTIVE, CLOSED }

    private final int groupId;
    private String name;
    private String description;
    private String learningTopic;
    private String ownerId;
    private final LocalDate creationDate;
    private int maximumMembers;
    private Status status;
    private final ArrayList<String> memberIds = new ArrayList<>();

    public LearningGroup(int groupId, String name, String description, String learningTopic,
                         String ownerId, LocalDate creationDate, int maximumMembers) {
        if (groupId < 1) throw new IllegalArgumentException("Group ID must be positive.");
        if (creationDate == null) throw new IllegalArgumentException("Group creation date is required.");
        this.groupId = groupId;
        this.creationDate = creationDate;
        this.status = Status.ACTIVE;
        update(name, description, learningTopic, maximumMembers);
        this.ownerId = requireText(ownerId, "Group owner ID");
        this.memberIds.add(this.ownerId);
    }

    public int getGroupId() { return groupId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getLearningTopic() { return learningTopic; }
    public String getOwnerId() { return ownerId; }
    public LocalDate getCreationDate() { return creationDate; }
    public int getMaximumMembers() { return maximumMembers; }
    public Status getStatus() { return status; }
    public List<String> getMemberIds() { return List.copyOf(memberIds); }
    public int getAvailableSeats() { return maximumMembers - memberIds.size(); }
    public boolean hasMember(String studentId) {
        return studentId != null && memberIds.stream().anyMatch(id -> id.equalsIgnoreCase(studentId.trim()));
    }

    public void update(String name, String description, String learningTopic, int maximumMembers) {
        String validName = requireText(name, "Group name");
        String validDescription = requireText(description, "Group description");
        String validTopic = requireText(learningTopic, "Learning topic");
        if (maximumMembers < memberIds.size() || maximumMembers < 1 || maximumMembers > 500)
            throw new IllegalArgumentException(
                    "Group capacity must be between its current member count and 500.");
        this.name = validName;
        this.description = validDescription;
        this.learningTopic = validTopic;
        this.maximumMembers = maximumMembers;
    }

    public boolean addMember(String studentId) {
        String validId = requireText(studentId, "Student ID");
        if (hasMember(validId)) throw new IllegalArgumentException("Student already belongs to this group.");
        if (memberIds.size() >= maximumMembers)
            throw new IllegalStateException("The group has reached its maximum member capacity.");
        memberIds.add(validId);
        return true;
    }

    public boolean removeMember(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        if (studentId.trim().equalsIgnoreCase(ownerId))
            throw new IllegalStateException("The group owner cannot leave or be removed from their group.");
        return memberIds.removeIf(id -> id.equalsIgnoreCase(studentId.trim()));
    }

    public void setStatus(Status status) {
        if (status == null) throw new IllegalArgumentException("Group status is required.");
        this.status = status;
    }

    public void reassignStudent(String oldStudentId, String newStudentId) {
        String oldId = requireText(oldStudentId, "Old student ID");
        String newId = requireText(newStudentId, "New student ID");
        for (int index = 0; index < memberIds.size(); index++) {
            if (memberIds.get(index).equalsIgnoreCase(oldId)) memberIds.set(index, newId);
        }
        if (ownerId.equalsIgnoreCase(oldId)) ownerId = newId;
    }

    public void restoreMember(String studentId) {
        String validId = requireText(studentId, "Student ID");
        if (hasMember(validId)) throw new IllegalArgumentException("Group data contains duplicate membership.");
        if (memberIds.size() >= maximumMembers)
            throw new IllegalArgumentException("Group data exceeds its maximum member capacity.");
        memberIds.add(validId);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        return value.trim();
    }
}
