package service;

import model.ExchangeRequest;
import model.Skill;
import model.Student;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public class ExchangeRequestManager {
    private final StudentManager studentManager;
    private final ArrayList<ExchangeRequest> requests = new ArrayList<>();
    private int nextRequestId = 1;

    public ExchangeRequestManager(StudentManager studentManager) {
        if (studentManager == null) {
            throw new IllegalArgumentException("Student manager is required.");
        }
        this.studentManager = studentManager;
    }

    public ExchangeRequest createRequest(
            String requesterId,
            String recipientId,
            String offeredSkillName,
            String requestedSkillName) {
        Student requester = findRegisteredStudent(requesterId, "Requester");
        Student recipient = findRegisteredStudent(recipientId, "Recipient");
        if (requester.getId().equalsIgnoreCase(recipient.getId())) {
            throw new IllegalArgumentException("A student cannot request an exchange with themselves.");
        }

        Skill offeredSkill = findOfferedSkill(requester, offeredSkillName, "Requester");
        Skill requestedSkill = findOfferedSkill(recipient, requestedSkillName, "Recipient");
        ExchangeRequest request = new ExchangeRequest(
                nextRequestId++, requester, recipient, offeredSkill, requestedSkill);
        requests.add(request);
        return request;
    }

    public List<ExchangeRequest> getAllRequests() {
        return List.copyOf(requests);
    }

    public int deleteRequestsForStudent(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Student ID is required.");
        }
        String searchId = studentId.trim();
        int previousSize = requests.size();
        requests.removeIf(request ->
                request.getRequester().getId().equalsIgnoreCase(searchId)
                        || request.getRecipient().getId().equalsIgnoreCase(searchId));
        return previousSize - requests.size();
    }

    public void restoreRequest(
            int requestId,
            String requesterId,
            String recipientId,
            String offeredSkillName,
            String requestedSkillName,
            ExchangeRequest.Status status) {
        if (requestId < 1) {
            throw new IllegalArgumentException("Request ID must be positive.");
        }
        if (requests.stream().anyMatch(request -> request.getRequestId() == requestId)) {
            throw new IllegalArgumentException("Request data contains a duplicate request ID.");
        }
        if (status == null) {
            throw new IllegalArgumentException("Request status is required.");
        }

        Student requester = findRegisteredStudent(requesterId, "Requester");
        Student recipient = findRegisteredStudent(recipientId, "Recipient");
        Skill offeredSkill = findOfferedSkill(requester, offeredSkillName, "Requester");
        Skill requestedSkill = findOfferedSkill(recipient, requestedSkillName, "Recipient");
        ExchangeRequest request = new ExchangeRequest(
                requestId, requester, recipient, offeredSkill, requestedSkill);
        if (status != ExchangeRequest.Status.PENDING) {
            request.setStatus(status);
        }
        requests.add(request);
        nextRequestId = Math.max(nextRequestId, requestId + 1);
    }

    public void acceptRequest(int requestId, String recipientId) {
        updatePendingRequest(requestId, recipientId, ExchangeRequest.Status.ACCEPTED);
    }

    public void rejectRequest(int requestId, String recipientId) {
        updatePendingRequest(requestId, recipientId, ExchangeRequest.Status.REJECTED);
    }

    private void updatePendingRequest(
            int requestId, String recipientId, ExchangeRequest.Status newStatus) {
        ExchangeRequest request = findRequest(requestId);
        if (recipientId == null || recipientId.isBlank()
                || !request.getRecipient().getId().equalsIgnoreCase(recipientId.trim())) {
            throw new IllegalArgumentException("Only the recipient can respond to this request.");
        }
        request.setStatus(newStatus);
    }

    private Student findRegisteredStudent(String studentId, String role) {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException(role + " student ID is required.");
        }
        return studentManager.findStudentById(studentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        role + " student ID was not found."));
    }

    private Skill findOfferedSkill(Student student, String skillName, String role) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException(role + " skill name is required.");
        }
        String searchName = skillName.trim();
        return student.getSkillsToTeach().stream()
                .filter(skill -> skill.getName().equalsIgnoreCase(searchName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        role + " does not have that skill in their Can Teach list."));
    }

    private ExchangeRequest findRequest(int requestId) {
        return requests.stream()
                .filter(request -> request.getRequestId() == requestId)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Exchange request was not found."));
    }
}
