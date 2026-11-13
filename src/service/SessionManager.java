package service;

import model.Session;
import model.Skill;
import model.Student;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

public class SessionManager {
    private final StudentManager studentManager;
    private final ArrayList<Session> sessions = new ArrayList<>();
    private int nextSessionId = 1;

    public SessionManager(StudentManager studentManager) {
        if (studentManager == null) throw new IllegalArgumentException("Student manager is required.");
        this.studentManager = studentManager;
    }

    public Session createSession(String teacherId, String learnerId, String skillName,
                                 LocalDate date, LocalTime startTime, int durationMinutes) {
        Student teacher = findStudent(teacherId, "Teacher");
        Student learner = findStudent(learnerId, "Learner");
        if (teacher.getId().equalsIgnoreCase(learner.getId()))
            throw new IllegalArgumentException("A student cannot be both teacher and learner.");
        if (skillName == null || skillName.isBlank())
            throw new IllegalArgumentException("Skill name is required.");
        Skill skill = teacher.getSkillsToTeach().stream()
                .filter(item -> item.getName().equalsIgnoreCase(skillName.trim()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Teacher does not have that skill in their Can Teach list."));
        validateLearnerInterest(learner, skill.getName());
        validateFuture(date, startTime);
        Session session = new Session(nextSessionId, teacher, learner, skill, date, startTime, durationMinutes);
        sessions.add(session);
        nextSessionId++;
        return session;
    }

    public List<Session> getAllSessions() {
        return sessions.stream().sorted(Comparator.comparing(Session::getDate)
                .thenComparing(Session::getStartTime).thenComparingInt(Session::getSessionId)).toList();
    }

    public Session getSession(int sessionId) {
        return sessions.stream().filter(item -> item.getSessionId() == sessionId).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Session was not found."));
    }

    public boolean hasSessionsForStudent(String studentId) {
        if (studentId == null || studentId.isBlank())
            throw new IllegalArgumentException("Student ID is required.");
        String normalizedId = studentId.trim();
        return sessions.stream().anyMatch(session ->
                session.getTeacher().getId().equalsIgnoreCase(normalizedId)
                        || session.getLearner().getId().equalsIgnoreCase(normalizedId));
    }

    public void updateSession(int sessionId, String participantId, LocalDate date,
                              LocalTime startTime, int durationMinutes) {
        Session session = getSession(sessionId);
        requireParticipant(session, participantId);
        if (session.getStatus() != Session.Status.SCHEDULED)
            throw new IllegalStateException("Only scheduled sessions can be updated.");
        validateFuture(date, startTime);
        session.updateSchedule(date, startTime, durationMinutes);
    }

    public void cancelSession(int sessionId, String participantId) {
        Session session = getSession(sessionId);
        requireParticipant(session, participantId);
        session.setStatus(Session.Status.CANCELLED);
    }

    public void completeSession(int sessionId, String participantId) {
        Session session = getSession(sessionId);
        requireParticipant(session, participantId);
        session.setStatus(Session.Status.COMPLETED);
    }

    public void restoreSession(int sessionId, String teacherId, String learnerId, String skillName,
                               LocalDate date, LocalTime startTime, int durationMinutes,
                               Session.Status status) {
        if (sessionId < 1) throw new IllegalArgumentException("Session ID must be positive.");
        if (sessions.stream().anyMatch(item -> item.getSessionId() == sessionId))
            throw new IllegalArgumentException("Session data contains a duplicate session ID.");
        Student teacher = findStudent(teacherId, "Teacher");
        Student learner = findStudent(learnerId, "Learner");
        if (skillName == null || skillName.isBlank()) throw new IllegalArgumentException("Skill name is required.");
        if (status == null) throw new IllegalArgumentException("Session status is required.");
        Skill skill = teacher.getSkillsToTeach().stream()
                .filter(item -> item.getName().equalsIgnoreCase(skillName.trim()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Teacher does not have that skill in their Can Teach list."));
        validateLearnerInterest(learner, skill.getName());
        Session restored = new Session(sessionId, teacher, learner, skill, date, startTime, durationMinutes);
        if (status != Session.Status.SCHEDULED) restored.setStatus(status);
        sessions.add(restored);
        nextSessionId = Math.max(nextSessionId, sessionId + 1);
    }

    private Student findStudent(String id, String role) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException(role + " student ID is required.");
        return studentManager.findStudentById(id)
                .orElseThrow(() -> new IllegalArgumentException(role + " student ID was not found."));
    }

    private void validateFuture(LocalDate date, LocalTime time) {
        if (date == null || time == null) throw new IllegalArgumentException("Session date and start time are required.");
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Session cannot be scheduled in the past.");
    }

    private void validateLearnerInterest(Student learner, String skillName) {
        if (learner.getSkillsToLearn().stream().noneMatch(skill ->
                skill.getName().equalsIgnoreCase(skillName)))
            throw new IllegalArgumentException("Learner does not have that skill in their Wants to Learn list.");
    }

    private void requireParticipant(Session session, String participantId) {
        if (participantId == null || participantId.isBlank()
                || (!session.getTeacher().getId().equalsIgnoreCase(participantId.trim())
                && !session.getLearner().getId().equalsIgnoreCase(participantId.trim())))
            throw new IllegalArgumentException("Only a session participant can perform this action.");
    }
}
