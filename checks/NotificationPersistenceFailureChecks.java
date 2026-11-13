import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Verifies event persistence failures do not reverse already-saved API operations. */
public final class NotificationPersistenceFailureChecks {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(3)).build();

    public static void main(String[] args) throws Exception {
        int port = availablePort();
        Path data = Files.createTempDirectory("skillora-notification-failure-check");
        Path log = data.resolve("server.log");
        String base = "http://127.0.0.1:" + port;
        Process server = startServer(port, data, log);
        int cancelledSessionId;
        int completedSessionId;
        try {
            awaitHealth(server, base);
            post(base + "/api/students", """
                    {"id":"FAIL-A","name":"Teacher","email":"teacher-fail@example.edu","department":"CS","yearOfStudy":2,
                     "skillsToTeach":[{"name":"Java","category":"Technology"}],"skillsToLearn":[{"name":"Art","category":"Creative"}]}
                    """, 201);
            post(base + "/api/students", """
                    {"id":"FAIL-B","name":"Learner","email":"learner-fail@example.edu","department":"Arts","yearOfStudy":1,
                     "skillsToTeach":[{"name":"Art","category":"Creative"}],"skillsToLearn":[{"name":"Java","category":"Technology"}]}
                    """, 201);

            Path notificationPath = data.resolve("notifications.txt");
            Files.createDirectory(notificationPath);
            String exchange = post(base + "/api/exchanges", """
                    {"requesterId":"FAIL-A","recipientId":"FAIL-B","offeredSkillName":"Java","requestedSkillName":"Art"}
                    """, 201);
            int exchangeId = number(exchange, "requestId");
            requireSuccess(send(base + "/api/exchanges/" + exchangeId,
                    "{\"recipientId\":\"FAIL-B\",\"status\":\"ACCEPTED\"}", "PATCH"), 200);

            LocalDate day = LocalDate.now().plusDays(3);
            String first = post(base + "/api/sessions", sessionBody(day), 201);
            cancelledSessionId = number(first, "sessionId");
            requireSuccess(send(base + "/api/sessions/" + cancelledSessionId,
                    "{\"participantId\":\"FAIL-B\",\"date\":\"" + day.plusDays(1)
                            + "\",\"startTime\":\"21:00\",\"durationMinutes\":50}", "PUT"), 200);
            requireSuccess(send(base + "/api/sessions/" + cancelledSessionId,
                    "{\"participantId\":\"FAIL-A\",\"status\":\"CANCELLED\"}", "PATCH"), 200);

            String second = post(base + "/api/sessions", sessionBody(day.plusDays(2)), 201);
            completedSessionId = number(second, "sessionId");
            requireSuccess(send(base + "/api/sessions/" + completedSessionId,
                    "{\"participantId\":\"FAIL-B\",\"status\":\"COMPLETED\"}", "PATCH"), 200);

            assertSavedOperationState(base, exchangeId, cancelledSessionId, completedSessionId);
            assertNoNotifications(base);
            try (var files = Files.list(notificationPath)) {
                if (files.findAny().isPresent())
                    throw new AssertionError("Failed notification writes left temporary files behind.");
            }
            Files.delete(notificationPath);
        } finally {
            stop(server);
        }

        server = startServer(port, data, log);
        try {
            awaitHealth(server, base);
            assertSavedOperationState(base, 1, cancelledSessionId, completedSessionId);
            assertNoNotifications(base);
            long loggedFailures = Files.readAllLines(log).stream()
                    .filter(line -> line.contains("Notification persistence failed after")
                            && line.contains("notification was rolled back"))
                    .count();
            if (loggedFailures != 6)
                throw new AssertionError("Expected 6 logged notification persistence failures, got " + loggedFailures);
            System.out.println("Notification persistence failure checks passed (exchange plus five session events).");
        } finally {
            stop(server);
        }
    }

    private static String sessionBody(LocalDate day) {
        return """
                {"teacherId":"FAIL-A","learnerId":"FAIL-B","skillName":"Java","date":"%s","startTime":"20:00","durationMinutes":60}
                """.formatted(day);
    }

    private static void assertSavedOperationState(String base, int exchangeId,
                                                   int cancelledSessionId, int completedSessionId) throws Exception {
        HttpResponse<String> exchange = send(base + "/api/exchanges", null, "GET");
        requireSuccess(exchange, 200);
        if (!exchange.body().contains("\"requestId\":" + exchangeId)
                || !exchange.body().contains("\"status\":\"ACCEPTED\""))
            throw new AssertionError("Exchange operation was not preserved: " + exchange.body());
        HttpResponse<String> sessions = send(base + "/api/sessions", null, "GET");
        requireSuccess(sessions, 200);
        if (!sessions.body().contains("\"sessionId\":" + cancelledSessionId + "")
                || !sessions.body().contains("\"sessionId\":" + completedSessionId + "")
                || !sessions.body().contains("\"status\":\"CANCELLED\"")
                || !sessions.body().contains("\"status\":\"COMPLETED\""))
            throw new AssertionError("Session operations were not preserved: " + sessions.body());
    }

    private static void assertNoNotifications(String base) throws Exception {
        for (String studentId : new String[] { "FAIL-A", "FAIL-B" }) {
            String encoded = URLEncoder.encode(studentId, StandardCharsets.UTF_8);
            HttpResponse<String> response = send(base + "/api/notifications?studentId=" + encoded, null, "GET");
            requireSuccess(response, 200);
            if (!response.body().contains("\"notifications\":[]")
                    || !response.body().contains("\"unreadCount\":0"))
                throw new AssertionError("An unsaved notification remained in memory: " + response.body());
        }
    }

    private static Process startServer(int port, Path data, Path log) throws IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "--add-modules", "jdk.httpserver", "-Dskillora.port=" + port,
                "-Dskillora.dataDir=" + data, "-Dskillora.webDir=" + Path.of("web").toAbsolutePath(),
                "-cp", System.getProperty("java.class.path"), "api.ApiServer")
                .directory(Path.of(".").toAbsolutePath().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.appendTo(log.toFile())).start();
    }

    private static String post(String url, String body, int status) throws Exception {
        HttpResponse<String> response = send(url, body, "POST");
        requireSuccess(response, status);
        return response.body();
    }

    private static HttpResponse<String> send(String url, String body, String method) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(5)).header("Accept", "application/json");
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void requireSuccess(HttpResponse<String> response, int status) {
        if (response.statusCode() != status || !response.body().contains("\"success\":true"))
            throw new AssertionError("Expected successful HTTP " + status + ", got "
                    + response.statusCode() + ": " + response.body());
    }

    private static int number(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing " + field + " in " + json);
        return Integer.parseInt(matcher.group(1));
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static void awaitHealth(Process server, String base) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (!server.isAlive()) throw new AssertionError("API server exited before startup.");
            try {
                HttpResponse<String> response = send(base + "/api/health", null, "GET");
                if (response.statusCode() == 200) return;
            } catch (IOException ignored) { Thread.sleep(100); }
        }
        throw new AssertionError("API server did not become ready.");
    }

    private static void stop(Process server) throws InterruptedException {
        server.destroyForcibly();
        server.waitFor();
    }
}
