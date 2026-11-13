import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Stream;

/** End-to-end HTTP workflow joining sessions, notifications, ratings, and portfolios. */
public final class SkilloraThreeModuleIntegrationChecks {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TEACHER_ID = "THREE-MODULE-TEACHER";
    private static final String LEARNER_ID = "THREE-MODULE-LEARNER";
    private static final String TEACHER_NAME = "Module Teacher";
    private static final String LEARNER_NAME = "Module Learner";
    private static final String SKILL = "Java";
    private static final String TEACHER_FEEDBACK = "Clear explanations and useful examples.";
    private static final String LEARNER_FEEDBACK = "Patient teacher and practical lesson.";

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Files.createTempDirectory("skillora-three-module-integration-");
        Process api = null;
        try {
            int port = availablePort();
            String base = "http://127.0.0.1:" + port;
            api = startApi(port, dataDirectory);
            awaitHealth(api, base);

            createStudent(base, TEACHER_ID, TEACHER_NAME, SKILL, "Drawing");
            createStudent(base, LEARNER_ID, LEARNER_NAME, "Drawing", SKILL);

            HttpResponse<String> scheduled = send(base + "/api/sessions", """
                    {"teacherId":"%s","learnerId":"%s","skillName":"%s",
                     "date":"%s","startTime":"22:15","durationMinutes":60}
                    """.formatted(TEACHER_ID, LEARNER_ID, SKILL, LocalDate.now().plusDays(3)), "POST");
            assertEnvelope(scheduled, 201, true);
            JsonNode scheduledData = field(scheduled.body(), "data");
            int sessionId = scheduledData.path("sessionId").asInt(0);
            require(sessionId > 0
                            && "SCHEDULED".equals(scheduledData.path("status").asText())
                            && TEACHER_ID.equals(scheduledData.path("teacher").path("id").asText())
                            && LEARNER_ID.equals(scheduledData.path("learner").path("id").asText())
                            && SKILL.equals(scheduledData.path("skill").path("name").asText()),
                    "Session creation response has incorrect participants, skill, ID, or status: " + scheduledData);

            assertNotification(base, LEARNER_ID, "SESSION_CREATED",
                    "Session #" + sessionId + " was scheduled with " + TEACHER_NAME + ".", 1);
            assertNoNotifications(base, TEACHER_ID);

            HttpResponse<String> completed = send(base + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"" + LEARNER_ID + "\",\"status\":\"COMPLETED\"}", "PATCH");
            assertEnvelope(completed, 200, true);
            require("COMPLETED".equals(field(completed.body(), "status").asText()),
                    "Session completion response did not report COMPLETED.");
            assertNotification(base, TEACHER_ID, "SESSION_COMPLETED",
                    "Session #" + sessionId + " was completed.", 1);
            assertNotification(base, LEARNER_ID, "SESSION_CREATED",
                    "Session #" + sessionId + " was scheduled with " + TEACHER_NAME + ".", 1);

            HttpResponse<String> teacherRating = submitRating(base, sessionId, TEACHER_ID, 5, TEACHER_FEEDBACK,
                    LEARNER_ID, LEARNER_NAME);
            HttpResponse<String> learnerRating = submitRating(base, sessionId, LEARNER_ID, 3, LEARNER_FEEDBACK,
                    TEACHER_ID, TEACHER_NAME);
            verifySessionRatings(base, sessionId);
            verifyPortfolio(base, LEARNER_ID, LEARNER_NAME, 5.0, TEACHER_ID, TEACHER_NAME,
                    TEACHER_FEEDBACK, 5, sessionId);
            verifyPortfolio(base, TEACHER_ID, TEACHER_NAME, 3.0, LEARNER_ID, LEARNER_NAME,
                    LEARNER_FEEDBACK, 3, sessionId);

            stopApi(api);
            api = null;
            int restartPort = availablePort();
            String restartedBase = "http://127.0.0.1:" + restartPort;
            api = startApi(restartPort, dataDirectory);
            awaitHealth(api, restartedBase);

            HttpResponse<String> restoredSession = send(restartedBase + "/api/sessions/" + sessionId, null, "GET");
            assertEnvelope(restoredSession, 200, true);
            require("COMPLETED".equals(field(restoredSession.body(), "status").asText()),
                    "Completed session was not restored after restart.");
            verifySessionRatings(restartedBase, sessionId);
            verifyPortfolio(restartedBase, LEARNER_ID, LEARNER_NAME, 5.0, TEACHER_ID, TEACHER_NAME,
                    TEACHER_FEEDBACK, 5, sessionId);
            verifyPortfolio(restartedBase, TEACHER_ID, TEACHER_NAME, 3.0, LEARNER_ID, LEARNER_NAME,
                    LEARNER_FEEDBACK, 3, sessionId);
            assertNotification(restartedBase, LEARNER_ID, "SESSION_CREATED",
                    "Session #" + sessionId + " was scheduled with " + TEACHER_NAME + ".", 1);
            assertNotification(restartedBase, TEACHER_ID, "SESSION_COMPLETED",
                    "Session #" + sessionId + " was completed.", 1);

            System.out.println("Skillora Modules 8-10 end-to-end integration check passed.");
        } finally {
            try {
                if (api != null) stopApi(api);
            } finally {
                deleteRecursively(dataDirectory);
            }
        }
    }

    private static void createStudent(String base, String id, String name, String skillToTeach,
                                     String skillToLearn) throws Exception {
        String body = """
                {"id":"%s","name":"%s","email":"%s@example.test","department":"Computing","yearOfStudy":2,
                 "skillsToTeach":[{"name":"%s","category":"Technology"}],
                 "skillsToLearn":[{"name":"%s","category":"Technology"}]}
                """.formatted(id, name, id.toLowerCase(), skillToTeach, skillToLearn);
        HttpResponse<String> response = send(base + "/api/students", body, "POST");
        assertEnvelope(response, 201, true);
        require(id.equals(field(response.body(), "id").asText()),
                "Student creation returned the wrong ID for " + id + ".");
    }

    private static HttpResponse<String> submitRating(String base, int sessionId, String reviewerId,
                                                    int score, String feedback, String subjectId,
                                                    String subjectName) throws Exception {
        String body = JSON.createObjectNode().put("reviewerId", reviewerId).put("score", score)
                .put("feedback", feedback).toString();
        HttpResponse<String> response = send(base + "/api/sessions/" + sessionId + "/ratings", body, "POST");
        assertEnvelope(response, 201, true);
        JsonNode data = field(response.body(), "data");
        require(data.path("sessionId").asInt() == sessionId
                        && reviewerId.equals(data.path("reviewer").path("id").asText())
                        && subjectId.equals(data.path("subject").path("id").asText())
                        && subjectName.equals(data.path("subject").path("name").asText())
                        && data.path("score").asInt(-1) == score
                        && feedback.equals(data.path("feedback").asText()),
                "Rating response has incorrect reviewer, subject, score, or feedback: " + data);
        return response;
    }

    private static void verifySessionRatings(String base, int sessionId) throws Exception {
        HttpResponse<String> response = send(base + "/api/sessions/" + sessionId + "/ratings", null, "GET");
        assertEnvelope(response, 200, true);
        JsonNode ratings = field(response.body(), "data");
        require(ratings.isArray() && ratings.size() == 2,
                "Expected reciprocal ratings for session #" + sessionId + ", got: " + ratings);
        assertRating(ratings, TEACHER_ID, LEARNER_ID, 5, TEACHER_FEEDBACK);
        assertRating(ratings, LEARNER_ID, TEACHER_ID, 3, LEARNER_FEEDBACK);
    }

    private static void assertRating(JsonNode ratings, String reviewerId, String subjectId,
                                     int score, String feedback) {
        for (JsonNode rating : ratings) {
            if (reviewerId.equals(rating.path("reviewer").path("id").asText())) {
                require(subjectId.equals(rating.path("subject").path("id").asText())
                                && rating.path("score").asInt(-1) == score
                                && feedback.equals(rating.path("feedback").asText()),
                        "Rating from " + reviewerId + " has incorrect subject, score, or feedback: " + rating);
                return;
            }
        }
        throw new AssertionError("Missing rating from " + reviewerId + ". Ratings: " + ratings);
    }

    private static void verifyPortfolio(String base, String studentId, String studentName, double average,
                                        String reviewerId, String reviewerName, String feedback,
                                        int score, int sessionId) throws Exception {
        HttpResponse<String> response = send(base + "/api/students/" + studentId + "/portfolio", null, "GET");
        assertEnvelope(response, 200, true);
        JsonNode portfolio = field(response.body(), "data");
        JsonNode sessions = portfolio.path("completedSessions");
        JsonNode ratings = portfolio.path("receivedRatings");
        require(studentId.equals(portfolio.path("studentId").asText())
                        && studentName.equals(portfolio.path("name").asText())
                        && sessions.isArray() && sessions.size() == 1
                        && sessions.get(0).path("sessionId").asInt() == sessionId
                        && ratings.isArray() && ratings.size() == 1
                        && portfolio.path("ratingCount").asInt(-1) == 1
                        && Math.abs(portfolio.path("averageRating").asDouble(-1) - average) < 0.0001,
                "Portfolio has incorrect profile, completed-session count, or rating aggregate: " + portfolio);
        JsonNode received = ratings.get(0);
        require(received.path("sessionId").asInt() == sessionId
                        && reviewerId.equals(received.path("reviewer").path("id").asText())
                        && reviewerName.equals(received.path("reviewer").path("name").asText())
                        && received.path("score").asInt(-1) == score
                        && feedback.equals(received.path("feedback").asText()),
                "Portfolio feedback attribution is incorrect: " + received);
        boolean hasFirstSessionAchievement = false;
        for (JsonNode achievement : portfolio.path("achievements")) {
            if ("COMPLETED_SESSIONS_1".equals(achievement.path("code").asText()))
                hasFirstSessionAchievement = true;
        }
        require(hasFirstSessionAchievement, "Portfolio is missing the first-completed-session achievement.");
    }

    private static void assertNotification(String base, String studentId, String eventType,
                                           String message, int unreadCount) throws Exception {
        String encodedId = java.net.URLEncoder.encode(studentId, java.nio.charset.StandardCharsets.UTF_8);
        HttpResponse<String> response = send(base + "/api/notifications?studentId=" + encodedId, null, "GET");
        assertEnvelope(response, 200, true);
        JsonNode data = field(response.body(), "data");
        JsonNode notifications = data.path("notifications");
        require(notifications.isArray() && notifications.size() == 1
                        && data.path("unreadCount").asInt(-1) == unreadCount,
                "Unexpected notifications or unread count for " + studentId + ": " + data);
        JsonNode notification = notifications.get(0);
        require(studentId.equals(notification.path("recipientId").asText())
                        && eventType.equals(notification.path("eventType").asText())
                        && message.equals(notification.path("message").asText())
                        && !notification.path("read").asBoolean(),
                "Notification recipient, event, message, or unread flag is incorrect: " + notification);
    }

    private static void assertNoNotifications(String base, String studentId) throws Exception {
        String encodedId = java.net.URLEncoder.encode(studentId, java.nio.charset.StandardCharsets.UTF_8);
        HttpResponse<String> response = send(base + "/api/notifications?studentId=" + encodedId, null, "GET");
        assertEnvelope(response, 200, true);
        JsonNode data = field(response.body(), "data");
        require(data.path("notifications").isArray() && data.path("notifications").isEmpty()
                        && data.path("unreadCount").asInt(-1) == 0,
                "Expected no notifications for " + studentId + ", got " + data);
    }

    private static void assertEnvelope(HttpResponse<String> response, int expectedStatus, boolean success)
            throws IOException {
        if (response.statusCode() != expectedStatus)
            throw new AssertionError("Expected HTTP " + expectedStatus + ", got " + response.statusCode()
                    + ": " + response.body());
        JsonNode body = JSON.readTree(response.body());
        require(body.path("success").asBoolean() == success && body.has("data") && body.has("error"),
                "Response envelope mismatch: " + body);
        if (success) require(!body.path("data").isNull() && body.path("error").isNull(),
                "Successful response has invalid data/error fields: " + body);
        else require(body.path("data").isNull() && !body.path("error").asText().isBlank(),
                "Error response has invalid data/error fields: " + body);
    }

    private static JsonNode field(String responseBody, String name) throws IOException {
        JsonNode root = JSON.readTree(responseBody);
        JsonNode data = root == null ? null : root.get("data");
        if (data == null) throw new AssertionError("Response is missing data: " + responseBody);
        return name.equals("data") ? data : data.path(name);
    }

    private static HttpResponse<String> send(String url, String body, String method) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5)).header("Accept", "application/json");
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Process startApi(int port, Path dataDirectory) throws IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "--add-modules", "jdk.httpserver", "-Dskillora.port=" + port,
                "-Dskillora.dataDir=" + dataDirectory.toAbsolutePath(),
                "-Dskillora.webDir=" + Path.of("web").toAbsolutePath(),
                "-cp", System.getProperty("java.class.path"), "api.ApiServer")
                .directory(Path.of(".").toAbsolutePath().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static void awaitHealth(Process server, String base) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (!server.isAlive()) throw new AssertionError("API server exited before startup.");
            try {
                HttpResponse<String> response = send(base + "/api/health", null, "GET");
                if (response.statusCode() == 200) {
                    assertEnvelope(response, 200, true);
                    return;
                }
            } catch (IOException ignored) {
                Thread.sleep(100);
            }
        }
        throw new AssertionError("API server did not become healthy.");
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static void stopApi(Process api) throws InterruptedException {
        if (!api.isAlive()) return;
        api.destroy();
        if (!api.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
            api.destroyForcibly();
            api.waitFor();
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
