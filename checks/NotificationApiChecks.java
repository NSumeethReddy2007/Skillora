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

/** HTTP checks for Module 9 event delivery, scoping, read state, and restart persistence. */
public final class NotificationApiChecks {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        int port = availablePort();
        Path data = Files.createTempDirectory("skillora-notification-api-check");
        String base = "http://127.0.0.1:" + port;
        Process server = startServer(port, data);
        try {
            awaitHealth(server, base);
            System.out.println("Notification API health check passed.");
            post(base + "/api/students", """
                    {"id":"NOTIF-A","name":"Alex","email":"alex-notif@example.edu","department":"CS","yearOfStudy":2,
                     "skillsToTeach":[{"name":"Java","category":"Technology"}],"skillsToLearn":[{"name":"Design","category":"Creative"}]}
                    """, 201);
            post(base + "/api/students", """
                    {"id":"NOTIF-B","name":"Blair","email":"blair-notif@example.edu","department":"Arts","yearOfStudy":1,
                     "skillsToTeach":[{"name":"Design","category":"Creative"}],"skillsToLearn":[{"name":"Java","category":"Technology"}]}
                    """, 201);

            String acceptedId = createExchange(base);
            patch(base + "/api/exchanges/" + acceptedId,
                    "{\"recipientId\":\"NOTIF-A\",\"status\":\"ACCEPTED\"}", 400);
            assertNotifications(base, "NOTIF-A", 0, 0);
            patch(base + "/api/exchanges/" + acceptedId,
                    "{\"recipientId\":\"NOTIF-B\",\"status\":\"ACCEPTED\"}", 200);
            String rejectedId = createExchange(base);
            patch(base + "/api/exchanges/" + rejectedId,
                    "{\"recipientId\":\"NOTIF-B\",\"status\":\"REJECTED\"}", 200);
            assertNotifications(base, "NOTIF-A", 2, 2, "EXCHANGE_ACCEPTED", "EXCHANGE_REJECTED");
            assertNotifications(base, "NOTIF-B", 0, 0);
            System.out.println("Exchange acceptance and rejection notification checks passed.");

            LocalDate day = LocalDate.now().plusDays(3);
            String created = post(base + "/api/sessions", """
                    {"teacherId":"NOTIF-A","learnerId":"NOTIF-B","skillName":"Java","date":"%s","startTime":"20:00","durationMinutes":60}
                    """.formatted(day), 201);
            int sessionId = integerField(created, "sessionId");
            assertNotifications(base, "NOTIF-B", 1, 1, "SESSION_CREATED");
            System.out.println("Session creation and failed-operation checks passed.");
            post(base + "/api/sessions", """
                    {"teacherId":"NOTIF-A","learnerId":"NOTIF-B","skillName":"Missing","date":"%s","startTime":"20:00","durationMinutes":60}
                    """.formatted(day), 400);
            assertNotifications(base, "NOTIF-B", 1, 1, "SESSION_CREATED");

            patchPut(base + "/api/sessions/" + sessionId, """
                    {"participantId":"NOTIF-B","date":"%s","startTime":"21:00","durationMinutes":45}
                    """.formatted(day.plusDays(1)), "PUT", 200);
            assertNotifications(base, "NOTIF-A", 3, 3, "SESSION_RESCHEDULED");
            patch(base + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"NOTIF-A\",\"status\":\"CANCELLED\"}", 200);
            assertNotifications(base, "NOTIF-B", 2, 2, "SESSION_CANCELLED");
            patch(base + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"NOTIF-A\",\"status\":\"CANCELLED\"}", 409);
            assertNotifications(base, "NOTIF-B", 2, 2);
            System.out.println("Session rescheduling and cancellation notification checks passed.");

            String completed = post(base + "/api/sessions", """
                    {"teacherId":"NOTIF-A","learnerId":"NOTIF-B","skillName":"Java","date":"%s","startTime":"20:00","durationMinutes":60}
                    """.formatted(day), 201);
            int completedSessionId = integerField(completed, "sessionId");
            patch(base + "/api/sessions/" + completedSessionId,
                    "{\"participantId\":\"NOTIF-B\",\"status\":\"COMPLETED\"}", 200);
            assertNotifications(base, "NOTIF-A", 4, 4, "SESSION_COMPLETED");

            HttpResponse<String> notifications = getNotifications(base, "NOTIF-A");
            int notificationId = integerField(notifications.body(), "notificationId");
            patch(base + "/api/notifications/" + notificationId,
                    "{\"studentId\":\"NOTIF-B\"}", 400);
            assertNotifications(base, "NOTIF-A", 4, 4);
            patch(base + "/api/notifications/" + notificationId,
                    "{\"studentId\":\"NOTIF-A\"}", 200);
            assertNotifications(base, "NOTIF-A", 4, 3);
            System.out.println("Notification retrieval, recipient validation, and read-state checks passed.");
            assertStatus(getNotifications(base, "unknown"), 400);
            assertStatus(getNotifications(base, " "), 400);
            assertStatus(send(base + "/api/notifications/" + notificationId,
                    "{\"studentId\":\"NOTIF-A\"}", "POST"), 405);
        } finally {
            stop(server);
        }

        server = startServer(port, data);
        try {
            awaitHealth(server, base);
            assertNotifications(base, "NOTIF-A", 4, 3, "EXCHANGE_ACCEPTED", "EXCHANGE_REJECTED",
                    "SESSION_RESCHEDULED", "SESSION_COMPLETED");
            assertNotifications(base, "NOTIF-B", 3, 3, "SESSION_CREATED", "SESSION_CANCELLED");
            if (!Files.isRegularFile(data.resolve("notifications.txt")))
                throw new AssertionError("notifications.txt was not written.");
            System.out.println("Notification API checks passed.");
        } finally {
            stop(server);
        }
    }

    private static Process startServer(int port, Path data) throws IOException {
        return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "--add-modules", "jdk.httpserver", "-Dskillora.port=" + port,
                "-Dskillora.dataDir=" + data, "-Dskillora.webDir=" + Path.of("web").toAbsolutePath(),
                "-cp", System.getProperty("java.class.path"), "api.ApiServer")
                .directory(Path.of(".").toAbsolutePath().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    private static void stop(Process process) throws InterruptedException {
        process.destroyForcibly();
        process.waitFor();
    }

    private static String createExchange(String base) throws Exception {
        String body = post(base + "/api/exchanges", """
                {"requesterId":"NOTIF-A","recipientId":"NOTIF-B","offeredSkillName":"Java","requestedSkillName":"Design"}
                """, 201);
        return Integer.toString(integerField(body, "requestId"));
    }

    private static void assertNotifications(String base, String studentId, int size, int unread,
                                            String... eventTypes) throws Exception {
        HttpResponse<String> response = getNotifications(base, studentId);
        assertStatus(response, 200);
        String body = response.body();
        if (occurrences(body, "\"notificationId\"") != size)
            throw new AssertionError(studentId + " notification count mismatch: " + body);
        if (integerField(body, "unreadCount") != unread)
            throw new AssertionError(studentId + " unread count mismatch: " + body);
        for (String eventType : eventTypes) if (!body.contains(eventType))
            throw new AssertionError("Missing " + eventType + " for " + studentId + ": " + body);
    }

    private static HttpResponse<String> getNotifications(String base, String studentId) throws Exception {
        String encoded = URLEncoder.encode(studentId, StandardCharsets.UTF_8);
        return send(base + "/api/notifications?studentId=" + encoded, null, "GET");
    }

    private static String post(String url, String body, int status) throws Exception {
        HttpResponse<String> response = send(url, body, "POST");
        assertStatus(response, status);
        return response.body();
    }

    private static void patch(String url, String body, int status) throws Exception {
        patchPut(url, body, "PATCH", status);
    }

    private static void patchPut(String url, String body, String method, int status) throws Exception {
        assertStatus(send(url, body, method), status);
    }

    private static HttpResponse<String> send(String url, String body, String method) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(5)).header("Accept", "application/json");
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertStatus(HttpResponse<String> response, int expected) {
        if (response.statusCode() != expected) throw new AssertionError("Expected HTTP " + expected
                + " but got " + response.statusCode() + ": " + response.body());
    }

    private static int integerField(String json, String field) {
        Matcher match = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!match.find()) throw new AssertionError("Missing numeric field " + field + " in " + json);
        return Integer.parseInt(match.group(1));
    }

    private static int occurrences(String text, String fragment) {
        Matcher matcher = Pattern.compile(Pattern.quote(fragment)).matcher(text);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static void awaitHealth(Process server, String base) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (!server.isAlive()) throw new AssertionError("API server exited before startup.");
            try {
                HttpResponse<String> response = send(base + "/api/health", null, "GET");
                if (response.statusCode() == 200) return;
            } catch (IOException ignored) { Thread.sleep(100); }
        }
        throw new AssertionError("API server did not become ready.");
    }
}
