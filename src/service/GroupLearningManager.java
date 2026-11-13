package service;

import model.GroupLearningSession;
import model.LearningGroup;
import model.Notification;
import model.Student;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

public final class GroupLearningManager {
    private final StudentManager studentManager;
    private final NotificationManager notificationManager;
    private final ArrayList<LearningGroup> groups = new ArrayList<>();
    private final ArrayList<GroupLearningSession> sessions = new ArrayList<>();
    private int nextGroupId = 1;
    private int nextSessionId = 1;

    public GroupLearningManager(StudentManager studentManager, NotificationManager notificationManager) {
        if (studentManager == null || notificationManager == null)
            throw new IllegalArgumentException("Student and notification managers are required.");
        this.studentManager = studentManager;
        this.notificationManager = notificationManager;
    }

    public LearningGroup createGroup(String ownerId, String name, String description,
                                     String learningTopic, int maximumMembers) {
        Student owner = findStudent(ownerId);
        ensureGroupCapacity();
        LearningGroup group = new LearningGroup(nextGroupId, name, description, learningTopic,
                owner.getId(), LocalDate.now(), maximumMembers);
        groups.add(group);
        nextGroupId = Math.incrementExact(nextGroupId);
        return group;
    }

    public List<LearningGroup> getAllGroups() {
        return groups.stream().sorted(Comparator.comparingInt(LearningGroup::getGroupId)).toList();
    }

    public List<LearningGroup> getGroupsForStudent(String studentId) {
        Student student = findStudent(studentId);
        return getAllGroups().stream().filter(group -> group.hasMember(student.getId())).toList();
    }

    public LearningGroup getGroup(int groupId) {
        return groups.stream().filter(group -> group.getGroupId() == groupId).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Learning group was not found."));
    }

    public void updateGroup(int groupId, String ownerId, String name, String description,
                            String learningTopic, int maximumMembers) {
        LearningGroup group = requireOwner(groupId, ownerId);
        group.update(name, description, learningTopic, maximumMembers);
    }

    public void setGroupStatus(int groupId, String ownerId, LearningGroup.Status status) {
        requireOwner(groupId, ownerId).setStatus(status);
    }

    public LearningGroup joinGroup(int groupId, String studentId) {
        LearningGroup group = getGroup(groupId);
        Student student = findStudent(studentId);
        if (group.getStatus() != LearningGroup.Status.ACTIVE)
            throw new IllegalStateException("Closed groups cannot accept new members.");
        group.addMember(student.getId());
        notificationManager.createNotification(group.getOwnerId(), "GROUP_MEMBER_JOINED",
                student.getName() + " (" + student.getId() + ") joined learning group \""
                        + group.getName() + "\" (#" + group.getGroupId() + ").");
        return group;
    }

    public LearningGroup leaveGroup(int groupId, String studentId) {
        LearningGroup group = getGroup(groupId);
        Student student = findStudent(studentId);
        if (!group.hasMember(student.getId()))
            throw new IllegalArgumentException("Student is not a member of this group.");
        if (student.getId().equalsIgnoreCase(group.getOwnerId()))
            throw new IllegalStateException("The group owner cannot leave while they are the group's only owner.");
        ensureNoOrganizedScheduledSessions(group, student.getId());
        group.removeMember(student.getId());
        return group;
    }

    public LearningGroup removeMember(int groupId, String ownerId, String memberId) {
        LearningGroup group = requireOwner(groupId, ownerId);
        Student member = findStudent(memberId);
        if (!group.hasMember(member.getId()))
            throw new IllegalArgumentException("Student is not a member of this group.");
        ensureNoOrganizedScheduledSessions(group, member.getId());
        group.removeMember(member.getId());
        notificationManager.createNotification(member.getId(), "GROUP_MEMBER_REMOVED",
                "You were removed from learning group \"" + group.getName()
                        + "\" (#" + group.getGroupId() + ").");
        return group;
    }

    public boolean hasReferencesForStudent(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        String normalizedId = studentId.trim();
        return groups.stream().anyMatch(group -> group.hasMember(normalizedId))
                || sessions.stream().anyMatch(session -> session.getOrganizerId().equalsIgnoreCase(normalizedId));
    }

    public void updateStudentId(String oldStudentId, String newStudentId) {
        if (oldStudentId == null || oldStudentId.isBlank())
            throw new IllegalArgumentException("Old student ID is required.");
        Student newStudent = findStudent(newStudentId);
        groups.forEach(group -> group.reassignStudent(oldStudentId, newStudent.getId()));
        sessions.stream()
                .filter(session -> session.getOrganizerId().equalsIgnoreCase(oldStudentId.trim()))
                .forEach(session -> session.reassignOrganizer(newStudent.getId()));
    }

    public GroupLearningSession scheduleSession(int groupId, String organizerId, String title,
                                                String description, LocalDate date, LocalTime time,
                                                int durationMinutes) {
        LearningGroup group = getGroup(groupId);
        Student organizer = requireMember(group, organizerId);
        if (group.getStatus() != LearningGroup.Status.ACTIVE)
            throw new IllegalStateException("New sessions can only be scheduled for active groups.");
        validateFuture(date, time);
        ensureSessionCapacity();
        GroupLearningSession session = new GroupLearningSession(nextSessionId, groupId, title,
                description, date, time, durationMinutes, organizer.getId());
        sessions.add(session);
        nextSessionId = Math.incrementExact(nextSessionId);
        notifyGroupMembers(group, "GROUP_SESSION_SCHEDULED",
                "Group session \"" + session.getTitle() + "\" (#" + session.getGroupSessionId()
                        + ") was scheduled for " + date + " at " + time + ".");
        return session;
    }

    public List<GroupLearningSession> getAllSessions() {
        return sessions.stream().sorted(Comparator.comparing(GroupLearningSession::getScheduledDate)
                .thenComparing(GroupLearningSession::getScheduledTime)
                .thenComparingInt(GroupLearningSession::getGroupSessionId)).toList();
    }

    public List<GroupLearningSession> getSessionsForGroup(int groupId) {
        getGroup(groupId);
        return getAllSessions().stream().filter(session -> session.getGroupId() == groupId).toList();
    }

    public GroupLearningSession getSession(int groupSessionId) {
        return sessions.stream().filter(session -> session.getGroupSessionId() == groupSessionId)
                .findFirst().orElseThrow(() -> new NoSuchElementException("Group session was not found."));
    }

    public void rescheduleSession(int groupSessionId, String organizerId, String title, String description,
                                  LocalDate date, LocalTime time, int durationMinutes) {
        GroupLearningSession session = getSession(groupSessionId);
        LearningGroup group = getGroup(session.getGroupId());
        requireSessionOrganizer(session, group, organizerId);
        validateFuture(date, time);
        LocalDate oldDate = session.getScheduledDate();
        LocalTime oldTime = session.getScheduledTime();
        String oldTitle = session.getTitle();
        String oldDescription = session.getDescription();
        int oldDuration = session.getDurationMinutes();
        session.update(title, description, date, time, durationMinutes);
        if (oldDate.equals(session.getScheduledDate()) && oldTime.equals(session.getScheduledTime())
                && oldTitle.equals(session.getTitle()) && oldDescription.equals(session.getDescription())
                && oldDuration == session.getDurationMinutes()) return;
        notifyGroupMembers(group, "GROUP_SESSION_RESCHEDULED",
                "Group session \"" + session.getTitle() + "\" (#" + session.getGroupSessionId()
                        + ") was rescheduled from " + oldDate + " at " + oldTime
                        + " to " + date + " at " + time + ".");
    }

    public void cancelSession(int groupSessionId, String organizerId) {
        GroupLearningSession session = getSession(groupSessionId);
        LearningGroup group = getGroup(session.getGroupId());
        requireSessionOrganizer(session, group, organizerId);
        session.setStatus(GroupLearningSession.Status.CANCELLED);
        notifyGroupMembers(group, "GROUP_SESSION_CANCELLED",
                "Group session \"" + session.getTitle() + "\" (#" + session.getGroupSessionId()
                        + ") was cancelled.");
    }

    public void completeSession(int groupSessionId, String organizerId) {
        GroupLearningSession session = getSession(groupSessionId);
        LearningGroup group = getGroup(session.getGroupId());
        requireSessionOrganizer(session, group, organizerId);
        session.setStatus(GroupLearningSession.Status.COMPLETED);
    }

    public void restoreGroup(int groupId, String name, String description, String topic, String ownerId,
                             LocalDate creationDate, int maximumMembers, LearningGroup.Status status,
                             List<String> memberIds) {
        if (groupId < 1) throw new IllegalArgumentException("Group ID must be positive.");
        if (groupId == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Group ID is too large to restore.");
        if (groups.stream().anyMatch(group -> group.getGroupId() == groupId))
            throw new IllegalArgumentException("Group data contains a duplicate group ID.");
        if (memberIds == null || memberIds.isEmpty())
            throw new IllegalArgumentException("Group must contain at least its owner.");
        Student owner = findStudent(ownerId);
        if (!owner.getId().equalsIgnoreCase(memberIds.getFirst()))
            throw new IllegalArgumentException("Group owner must be the first member.");
        LearningGroup group = new LearningGroup(groupId, name, description, topic, owner.getId(),
                creationDate, maximumMembers);
        for (String memberId : memberIds.subList(1, memberIds.size()))
            group.restoreMember(findStudent(memberId).getId());
        group.setStatus(status);
        groups.add(group);
        nextGroupId = Math.max(nextGroupId, groupId + 1);
    }

    public void restoreSession(int sessionId, int groupId, String title, String description,
                               LocalDate date, LocalTime time, int durationMinutes, String organizerId,
                               GroupLearningSession.Status status) {
        if (sessionId < 1) throw new IllegalArgumentException("Group session ID must be positive.");
        if (sessionId == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Group session ID is too large to restore.");
        if (sessions.stream().anyMatch(session -> session.getGroupSessionId() == sessionId))
            throw new IllegalArgumentException("Group session data contains a duplicate ID.");
        LearningGroup group = getGroup(groupId);
        Student organizer = requireMember(group, organizerId);
        if (status == null) throw new IllegalArgumentException("Group session status is required.");
        GroupLearningSession session = new GroupLearningSession(sessionId, groupId, title, description,
                date, time, durationMinutes, organizer.getId());
        if (status != null && status != GroupLearningSession.Status.SCHEDULED) session.setStatus(status);
        sessions.add(session);
        nextSessionId = Math.max(nextSessionId, sessionId + 1);
    }

    private LearningGroup requireOwner(int groupId, String ownerId) {
        LearningGroup group = getGroup(groupId);
        Student owner = findStudent(ownerId);
        if (!group.getOwnerId().equalsIgnoreCase(owner.getId()))
            throw new IllegalArgumentException("Only the group owner can perform this action.");
        return group;
    }

    private void requireSessionOrganizer(GroupLearningSession session, LearningGroup group, String studentId) {
        Student student = requireMember(group, studentId);
        if (!session.getOrganizerId().equalsIgnoreCase(student.getId())
                && !group.getOwnerId().equalsIgnoreCase(student.getId()))
            throw new IllegalArgumentException("Only the group session organizer or group owner can perform this action.");
        if (session.getStatus() != GroupLearningSession.Status.SCHEDULED)
            throw new IllegalStateException("Only scheduled group sessions can be changed.");
    }

    private Student requireMember(LearningGroup group, String studentId) {
        Student student = findStudent(studentId);
        if (!group.hasMember(student.getId()))
            throw new IllegalArgumentException("Only group members can organize or participate in group sessions.");
        return student;
    }

    private Student findStudent(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        return studentManager.findStudentById(studentId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Student profile was not found."));
    }

    private void validateFuture(LocalDate date, LocalTime time) {
        if (date == null || time == null)
            throw new IllegalArgumentException("Group session date and time are required.");
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Group session cannot be scheduled in the past.");
    }

    private void notifyGroupMembers(LearningGroup group, String eventType, String message) {
        for (String memberId : group.getMemberIds())
            notificationManager.createNotification(memberId, eventType, message);
    }

    private void ensureNoOrganizedScheduledSessions(LearningGroup group, String studentId) {
        boolean hasScheduledSession = sessions.stream()
                .anyMatch(session -> session.getGroupId() == group.getGroupId()
                        && session.getOrganizerId().equalsIgnoreCase(studentId)
                        && session.getStatus() == GroupLearningSession.Status.SCHEDULED);
        if (hasScheduledSession)
            throw new IllegalStateException(
                    "A member who organized a scheduled group session cannot leave until it is completed or cancelled.");
    }

    private void ensureGroupCapacity() {
        if (nextGroupId == Integer.MAX_VALUE)
            throw new IllegalStateException("Learning group IDs are exhausted.");
    }

    private void ensureSessionCapacity() {
        if (nextSessionId == Integer.MAX_VALUE)
            throw new IllegalStateException("Group session IDs are exhausted.");
    }
}
