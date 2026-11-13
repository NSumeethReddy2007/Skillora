package model;

import java.time.LocalDate;
import java.time.LocalTime;

public class Session {
    public enum Status { SCHEDULED, COMPLETED, CANCELLED }

    private final int sessionId;
    private final Student teacher;
    private final Student learner;
    private final Skill skill;
    private LocalDate date;
    private LocalTime startTime;
    private int durationMinutes;
    private Status status;

    public Session(int sessionId, Student teacher, Student learner, Skill skill,
                   LocalDate date, LocalTime startTime, int durationMinutes) {
        if (sessionId < 1) throw new IllegalArgumentException("Session ID must be positive.");
        if (teacher == null || learner == null || skill == null)
            throw new IllegalArgumentException("Teacher, learner, and skill are required.");
        if (teacher.getId().equalsIgnoreCase(learner.getId()))
            throw new IllegalArgumentException("A student cannot be both teacher and learner.");
        this.sessionId = sessionId;
        this.teacher = teacher;
        this.learner = learner;
        this.skill = skill;
        this.status = Status.SCHEDULED;
        updateSchedule(date, startTime, durationMinutes);
    }

    public int getSessionId() { return sessionId; }
    public Student getTeacher() { return teacher; }
    public Student getLearner() { return learner; }
    public Skill getSkill() { return skill; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public Status getStatus() { return status; }

    public void updateSchedule(LocalDate date, LocalTime startTime, int durationMinutes) {
        if (status != Status.SCHEDULED)
            throw new IllegalStateException("Only scheduled sessions can be updated.");
        if (date == null || startTime == null)
            throw new IllegalArgumentException("Session date and start time are required.");
        if (durationMinutes < 1 || durationMinutes > 1440)
            throw new IllegalArgumentException("Session duration must be between 1 and 1440 minutes.");
        this.date = date;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
    }

    public void setStatus(Status status) {
        if (this.status != Status.SCHEDULED)
            throw new IllegalStateException("Only scheduled sessions can change status.");
        if (status != Status.COMPLETED && status != Status.CANCELLED)
            throw new IllegalArgumentException("A scheduled session can only be completed or cancelled.");
        this.status = status;
    }

}
