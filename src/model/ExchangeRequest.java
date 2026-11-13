package model;

public class ExchangeRequest {
    public enum Status {
        PENDING,
        ACCEPTED,
        REJECTED
    }

    private final int requestId;
    private final Student requester;
    private final Student recipient;
    private final Skill offeredSkill;
    private final Skill requestedSkill;
    private Status status;

    public ExchangeRequest(
            int requestId,
            Student requester,
            Student recipient,
            Skill offeredSkill,
            Skill requestedSkill) {
        if (requestId < 1) {
            throw new IllegalArgumentException("Request ID must be positive.");
        }
        if (requester == null || recipient == null) {
            throw new IllegalArgumentException("Requester and recipient are required.");
        }
        if (requester.getId().equalsIgnoreCase(recipient.getId())) {
            throw new IllegalArgumentException("A student cannot request an exchange with themselves.");
        }
        if (offeredSkill == null || requestedSkill == null) {
            throw new IllegalArgumentException("Offered and requested skills are required.");
        }

        this.requestId = requestId;
        this.requester = requester;
        this.recipient = recipient;
        this.offeredSkill = offeredSkill;
        this.requestedSkill = requestedSkill;
        this.status = Status.PENDING;
    }

    public int getRequestId() {
        return requestId;
    }

    public Student getRequester() {
        return requester;
    }

    public Student getRecipient() {
        return recipient;
    }

    public Skill getOfferedSkill() {
        return offeredSkill;
    }

    public Skill getRequestedSkill() {
        return requestedSkill;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Only pending requests can be updated.");
        }
        if (status != Status.ACCEPTED && status != Status.REJECTED) {
            throw new IllegalArgumentException("A pending request can only be accepted or rejected.");
        }
        this.status = status;
    }
}
