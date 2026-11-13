package api;

import storage.FileManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Failure-injection checks for transactional student ID updates and restart recovery. */
public final class StudentUpdateRollbackChecks {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String ORIGINAL_ID = "UPDATE-A";
    private static final String NEW_ID = "UPDATE-A-NEW";
    private static final String OTHER_ID = "UPDATE-B";
    private static final String[] DATA_FILES = {
            "students.txt", "requests.txt", "sessions.txt", "ratings.txt", "notifications.txt"
    };

    public static void main(String[] args) throws Exception {
        for (FileManager.PersistenceStage stage : FileManager.PersistenceStage.values()) {
            if (stage == FileManager.PersistenceStage.ROLLBACK
                    || stage == FileManager.PersistenceStage.ROADMAPS
                    || stage == FileManager.PersistenceStage.REWARDS
                    || stage == FileManager.PersistenceStage.GROUPS
                    || stage == FileManager.PersistenceStage.GROUP_SESSIONS) continue;
            verifyRollbackAt(stage);
        }
        verifyRollbackFailureIsNotReportedAsSuccess();
        System.out.println("Student update rollback checks passed at every persistence stage and rollback failure.");
    }

    private static void verifyRollbackAt(FileManager.PersistenceStage failureAt) throws Exception {
        Path data = Files.createTempDirectory("skillora-student-update-rollback-");
        AtomicReference<FileManager.PersistenceStage> failureStage = new AtomicReference<>();
        AtomicBoolean failRollback = new AtomicBoolean();
        FileManager files = new FileManager(data, stage -> {
            if (stage == FileManager.PersistenceStage.ROLLBACK && failRollback.compareAndSet(true, false))
                throw new IOException("Injected rollback failure");
            if (failureStage.compareAndSet(stage, null))
                throw new IOException("Injected persistence failure at " + stage);
        });
        int port = availablePort();
        ApiServer server = new ApiServer(port, files);
        server.start();
        int sessionId;
        Map<String, byte[]> originalFiles;
        try {
            String base = "http://127.0.0.1:" + port;
            assertStatus(send(base + "/api/students", student(ORIGINAL_ID, "Original"), "POST"), 201, true);
            assertStatus(send(base + "/api/students", student(OTHER_ID, "Other"), "POST"), 201, true);
            String created = send(base + "/api/sessions", """
                    {"teacherId":"UPDATE-A","learnerId":"UPDATE-B","skillName":"Java",
                     "date":"%s","startTime":"22:30","durationMinutes":60}
                    """.formatted(LocalDate.now().plusDays(2)), "POST").body();
            sessionId = number(created, "sessionId");
            assertStatus(send(base + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"UPDATE-B\",\"status\":\"COMPLETED\"}", "PATCH"), 200, true);
            assertStatus(send(base + "/api/sessions/" + sessionId + "/ratings",
                    "{\"reviewerId\":\"UPDATE-A\",\"score\":5,\"feedback\":\"Persisted review\"}",
                    "POST"), 201, true);

            originalFiles = snapshot(data);
            failureStage.set(failureAt);
            HttpResponse<String> failedUpdate = send(base + "/api/students/" + ORIGINAL_ID,
                    student(NEW_ID, "Changed"), "PUT");
            assertStatus(failedUpdate, 500, false);
            assertFilesEqual(originalFiles, data, "immediately after " + failureAt + " failure");
            assertOriginalApiState(base, sessionId);
        } finally {
            server.stop();
        }

        int restartPort = availablePort();
        ApiServer restarted = new ApiServer(restartPort, new FileManager(data));
        restarted.start();
        try {
            assertOriginalApiState("http://127.0.0.1:" + restartPort, sessionId);
            assertFilesEqual(originalFiles, data, "after restart");
        } finally {
            restarted.stop();
        }
    }

    private static void verifyRollbackFailureIsNotReportedAsSuccess() throws Exception {
        Path data = Files.createTempDirectory("skillora-student-update-rollback-failure-");
        AtomicReference<FileManager.PersistenceStage> failureStage = new AtomicReference<>();
        AtomicBoolean failRollback = new AtomicBoolean();
        FileManager files = new FileManager(data, stage -> {
            if (stage == FileManager.PersistenceStage.ROLLBACK && failRollback.compareAndSet(true, false))
                throw new IOException("Injected rollback failure");
            if (failureStage.compareAndSet(stage, null))
                throw new IOException("Injected persistence failure at " + stage);
        });
        int port = availablePort();
        ApiServer server = new ApiServer(port, files);
        server.start();
        try {
            String base = "http://127.0.0.1:" + port;
            assertStatus(send(base + "/api/students", student(ORIGINAL_ID, "Original"), "POST"), 201, true);
            assertStatus(send(base + "/api/students", student(OTHER_ID, "Other"), "POST"), 201, true);
            String created = send(base + "/api/sessions", """
                    {"teacherId":"UPDATE-A","learnerId":"UPDATE-B","skillName":"Java",
                     "date":"%s","startTime":"22:30","durationMinutes":60}
                    """.formatted(LocalDate.now().plusDays(2)), "POST").body();
            int sessionId = number(created, "sessionId");
            assertStatus(send(base + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"UPDATE-B\",\"status\":\"COMPLETED\"}", "PATCH"), 200, true);
            assertStatus(send(base + "/api/sessions/" + sessionId + "/ratings",
                    "{\"reviewerId\":\"UPDATE-A\",\"score\":5,\"feedback\":\"Persisted review\"}",
                    "POST"), 201, true);

            failureStage.set(FileManager.PersistenceStage.NOTIFICATIONS);
            failRollback.set(true);
            HttpResponse<String> failedUpdate = send(base + "/api/students/" + ORIGINAL_ID,
                    student(NEW_ID, "Changed"), "PUT");
            assertStatus(failedUpdate, 500, false);
            if (!failedUpdate.body().contains("rollback could not fully restore"))
                throw new AssertionError("Rollback failure was not made clear in the error response: "
                        + failedUpdate.body());
            assertOriginalApiState(base, sessionId);
        } finally {
            server.stop();
        }
    }

    private static void assertOriginalApiState(String base, int sessionId) throws Exception {
        assertStatus(send(base + "/api/students/" + ORIGINAL_ID, null, "GET"), 200, true);
        assertStatus(send(base + "/api/students/" + NEW_ID, null, "GET"), 404, false);
        HttpResponse<String> session = send(base + "/api/sessions/" + sessionId, null, "GET");
        assertStatus(session, 200, true);
        if (!session.body().contains("\"teacher\":{\"id\":\"" + ORIGINAL_ID + "\""))
            throw new AssertionError("Session participant ID was not restored: " + session.body());
        HttpResponse<String> ratings = send(base + "/api/sessions/" + sessionId + "/ratings", null, "GET");
        assertStatus(ratings, 200, true);
        if (!ratings.body().contains("\"reviewer\":{\"id\":\"" + ORIGINAL_ID + "\""))
            throw new AssertionError("Rating reviewer ID was not restored: " + ratings.body());
        HttpResponse<String> notifications = send(base + "/api/notifications?studentId=" + ORIGINAL_ID,
                null, "GET");
        assertStatus(notifications, 200, true);
        if (!notifications.body().contains("\"recipientId\":\"" + ORIGINAL_ID + "\""))
            throw new AssertionError("Notification recipient ID was not restored: " + notifications.body());
    }

    private static Map<String, byte[]> snapshot(Path directory) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String name : DATA_FILES) {
            Path file = directory.resolve(name);
            result.put(name, Files.exists(file) ? Files.readAllBytes(file) : null);
        }
        return result;
    }

    private static void assertFilesEqual(Map<String, byte[]> expected, Path directory, String when)
            throws IOException {
        for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
            Path file = directory.resolve(entry.getKey());
            byte[] actual = Files.exists(file) ? Files.readAllBytes(file) : null;
            if (!java.util.Arrays.equals(entry.getValue(), actual))
                throw new AssertionError(entry.getKey() + " changed " + when + ".");
        }
    }

    private static String student(String id, String name) {
        boolean teacher = !OTHER_ID.equals(id);
        String skillToTeach = teacher ? "Java" : "Drawing";
        String skillToLearn = teacher ? "Drawing" : "Java";
        return """
                {"id":"%s","name":"%s","email":"%s@example.edu","department":"CS","yearOfStudy":2,
                 "skillsToTeach":[{"name":"%s","category":"Technology"}],
                 "skillsToLearn":[{"name":"%s","category":"Technology"}]}
                """.formatted(id, name, id.toLowerCase(), skillToTeach, skillToLearn);
    }

    private static HttpResponse<String> send(String url, String body, String method) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(5)).header("Accept", "application/json");
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertStatus(HttpResponse<String> response, int status, boolean success) {
        String body = response.body();
        if (response.statusCode() != status || !body.contains("\"success\":" + success)
                || !body.contains("\"data\":") || !body.contains("\"error\":"))
            throw new AssertionError("Unexpected response: HTTP " + response.statusCode() + " " + body);
    }

    private static int number(String json, String field) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\\"" + field + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing " + field + " in " + json);
        return Integer.parseInt(matcher.group(1));
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }
}
