import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** HTTP integration check for exchange outcomes, notification ownership, read state, and restart persistence. */
public final class SkilloraExchangeNotificationIntegrationChecks {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REQUESTER = "EXCHANGE-REQUESTER";
    private static final String RECIPIENT = "EXCHANGE-RECIPIENT";
    private static final String UNRELATED = "EXCHANGE-UNRELATED";

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Files.createTempDirectory("skillora-exchange-notification-integration-");
        Process api = null;
        try {
            int port = availablePort();
            String base = "http://127.0.0.1:" + port;
            api = startApi(port, dataDirectory);
            awaitHealth(api, base);

            createStudent(base, REQUESTER, "Requester", "Java", "Drawing");
            createStudent(base, RECIPIENT, "Recipient", "Drawing", "Java");
            createStudent(base, UNRELATED, "Unrelated", "Rust", "Go");

            assertEmptyNotifications(base, REQUESTER);
            assertEmptyNotifications(base, RECIPIENT);
            assertEmptyNotifications(base, UNRELATED);
            require(!Files.exists(dataDirectory.resolve("notifications.txt")),
                    "Invalid or read-only operations created notifications.txt.");

            HttpResponse<String> invalidCreate = send(base + "/api/exchanges", exchangeBody("Missing skill"), "POST");
            assertEnvelope(invalidCreate, 400, false);
            assertEmptyNotifications(base, REQUESTER);
            assertNoNotificationFile(dataDirectory);

            int acceptedRequestId = createExchange(base);
            require(!Files.exists(dataDirectory.resolve("notifications.txt")),
                    "Creating a pending exchange unexpectedly created a notification.");
            HttpResponse<String> wrongRecipient = send(base + "/api/exchanges/" + acceptedRequestId,
                    "{\"recipientId\":\"" + REQUESTER + "\",\"status\":\"ACCEPTED\"}", "PATCH");
            assertEnvelope(wrongRecipient, 400, false);
            assertRequestStatus(base, acceptedRequestId, "PENDING");
            assertEmptyNotifications(base, REQUESTER);
            assertNoNotificationFile(dataDirectory);

            HttpResponse<String> accepted = send(base + "/api/exchanges/" + acceptedRequestId,
                    "{\"recipientId\":\"" + RECIPIENT + "\",\"status\":\"ACCEPTED\"}", "PATCH");
            assertEnvelope(accepted, 200, true);
            require(field(accepted.body(), "requestId").asInt() == acceptedRequestId
                            && "ACCEPTED".equals(field(accepted.body(), "status").asText()),
                    "Accept response did not contain the updated exchange request.");
            require(Files.exists(dataDirectory.resolve("notifications.txt")),
                    "Successful acceptance did not persist its notification.");
            int acceptedNotificationId = assertSingleNotification(base, REQUESTER,
                    "EXCHANGE_ACCEPTED", "Your exchange request #" + acceptedRequestId + " was accepted.", 1);
            assertEmptyNotifications(base, RECIPIENT);
            assertEmptyNotifications(base, UNRELATED);

            HttpResponse<String> wrongReader = send(base + "/api/notifications/" + acceptedNotificationId,
                    "{\"studentId\":\"" + RECIPIENT + "\"}", "PATCH");
            assertEnvelope(wrongReader, 400, false);
            assertSingleNotification(base, REQUESTER, "EXCHANGE_ACCEPTED",
                    "Your exchange request #" + acceptedRequestId + " was accepted.", 1);
            assertEmptyNotifications(base, RECIPIENT);
            assertEmptyNotifications(base, UNRELATED);

            HttpResponse<String> markedRead = send(base + "/api/notifications/" + acceptedNotificationId,
                    "{\"studentId\":\"" + REQUESTER + "\"}", "PATCH");
            assertEnvelope(markedRead, 200, true);
            require(field(markedRead.body(), "read").asBoolean(), "The authorized read update did not mark it read.");
            assertSingleNotification(base, REQUESTER, "EXCHANGE_ACCEPTED",
                    "Your exchange request #" + acceptedRequestId + " was accepted.", 0);

            int rejectedRequestId = createExchange(base);
            HttpResponse<String> rejected = send(base + "/api/exchanges/" + rejectedRequestId,
                    "{\"recipientId\":\"" + RECIPIENT + "\",\"status\":\"REJECTED\"}", "PATCH");
            assertEnvelope(rejected, 200, true);
            require(field(rejected.body(), "requestId").asInt() == rejectedRequestId
                            && "REJECTED".equals(field(rejected.body(), "status").asText()),
                    "Reject response did not contain the updated exchange request.");
            int rejectedNotificationId = assertNotificationPresent(base, REQUESTER,
                    "EXCHANGE_REJECTED", "Your exchange request #" + rejectedRequestId + " was rejected.", 1);
            assertEmptyNotifications(base, RECIPIENT);
            assertEmptyNotifications(base, UNRELATED);

            HttpResponse<String> wrongRejectionReader = send(base + "/api/notifications/" + rejectedNotificationId,
                    "{\"studentId\":\"" + UNRELATED + "\"}", "PATCH");
            assertEnvelope(wrongRejectionReader, 400, false);
            assertNotificationPresent(base, REQUESTER, "EXCHANGE_REJECTED",
                    "Your exchange request #" + rejectedRequestId + " was rejected.", 1);
            assertExactlyTwoRequesterNotifications(base, acceptedRequestId, rejectedRequestId);

            stopApi(api);
            api = null;
            int restartPort = availablePort();
            String restartedBase = "http://127.0.0.1:" + restartPort;
            api = startApi(restartPort, dataDirectory);
            awaitHealth(api, restartedBase);

            assertRequestStatus(restartedBase, acceptedRequestId, "ACCEPTED");
            assertRequestStatus(restartedBase, rejectedRequestId, "REJECTED");
            assertNotificationReadState(restartedBase, REQUESTER, "EXCHANGE_ACCEPTED", acceptedRequestId, true, 1);
            assertNotificationReadState(restartedBase, REQUESTER, "EXCHANGE_REJECTED", rejectedRequestId, false, 1);
            assertEmptyNotifications(restartedBase, RECIPIENT);
            assertEmptyNotifications(restartedBase, UNRELATED);
            System.out.println("Skillora exchange and notification lifecycle integration checks passed.");
        } finally {
            try {
                if (api != null) stopApi(api);
            } finally {
                deleteRecursively(dataDirectory);
            }
        }
    }

    private static void createStudent(String base, String id, String name, String teaches, String learns)
            throws Exception {
        String body = """
                {"id":"%s","name":"%s","email":"%s@example.test","department":"Computing","yearOfStudy":2,
                 "skillsToTeach":[{"name":"%s","category":"Technology"}],
                 "skillsToLearn":[{"name":"%s","category":"Technology"}]}
                """.formatted(id, name, id.toLowerCase(), teaches, learns);
        HttpResponse<String> response = send(base + "/api/students", body, "POST");
        assertEnvelope(response, 201, true);
        require(id.equals(field(response.body(), "id").asText()), "Student create response has the wrong ID.");
    }

    private static String exchangeBody(String offeredSkill) {
        return "{\"requesterId\":\"" + REQUESTER + "\",\"recipientId\":\"" + RECIPIENT
                + "\",\"offeredSkillName\":\"" + offeredSkill + "\",\"requestedSkillName\":\"Drawing\"}";
    }

    private static int createExchange(String base) throws Exception {
        HttpResponse<String> response = send(base + "/api/exchanges", exchangeBody("Java"), "POST");
        assertEnvelope(response, 201, true);
        JsonNode data = field(response.body(), "data");
        int requestId = data.path("requestId").asInt(0);
        require(requestId > 0
                        && REQUESTER.equals(data.path("requester").path("id").asText())
                        && RECIPIENT.equals(data.path("recipient").path("id").asText())
                        && "PENDING".equals(data.path("status").asText()),
                "Exchange create response is missing its participants or pending status.");
        return requestId;
    }

    private static void assertRequestStatus(String base, int requestId, String expectedStatus) throws Exception {
        HttpResponse<String> response = send(base + "/api/exchanges", null, "GET");
        assertEnvelope(response, 200, true);
        JsonNode requests = field(response.body(), "data");
        for (JsonNode request : requests) {
            if (request.path("requestId").asInt() == requestId) {
                require(expectedStatus.equals(request.path("status").asText()),
                        "Request #" + requestId + " expected " + expectedStatus + " but was "
                                + request.path("status").asText());
                return;
            }
        }
        throw new AssertionError("Exchange request #" + requestId + " was missing from GET /api/exchanges.");
    }

    private static int assertSingleNotification(String base, String studentId, String eventType,
                                               String message, int unreadCount) throws Exception {
        JsonNode data = notificationData(base, studentId);
        JsonNode notifications = data.path("notifications");
        require(notifications.size() == 1, "Expected exactly one notification for " + studentId
                + ", found " + notifications.size() + ".");
        assertNotification(notifications.get(0), studentId, eventType, message, unreadCount == 0);
        require(data.path("unreadCount").asInt(-1) == unreadCount,
                "Unexpected unread count for " + studentId + ": " + data.path("unreadCount"));
        return notifications.get(0).path("notificationId").asInt();
    }

    private static int assertNotificationPresent(String base, String studentId, String eventType,
                                                String message, int unreadCount) throws Exception {
        JsonNode data = notificationData(base, studentId);
        JsonNode found = null;
        for (JsonNode item : data.path("notifications")) {
            if (eventType.equals(item.path("eventType").asText())) found = item;
        }
        require(found != null, "Missing " + eventType + " notification for " + studentId + ".");
        assertNotification(found, studentId, eventType, message, false);
        require(data.path("unreadCount").asInt(-1) == unreadCount,
                "Unexpected unread count for " + studentId + ": " + data.path("unreadCount"));
        return found.path("notificationId").asInt();
    }

    private static void assertNotification(JsonNode notification, String recipient, String eventType,
                                          String message, boolean read) {
        require(notification.path("notificationId").asInt(0) > 0
                        && recipient.equals(notification.path("recipientId").asText())
                        && eventType.equals(notification.path("eventType").asText())
                        && message.equals(notification.path("message").asText())
                        && notification.path("read").asBoolean() == read,
                "Unexpected notification record: " + notification);
    }

    private static void assertNotificationReadState(String base, String studentId, String eventType,
                                                    int requestId, boolean expectedRead, int expectedUnreadCount)
            throws Exception {
        JsonNode data = notificationData(base, studentId);
        JsonNode found = null;
        for (JsonNode item : data.path("notifications")) {
            if (eventType.equals(item.path("eventType").asText())) found = item;
        }
        require(found != null
                        && found.path("message").asText().contains("#" + requestId)
                        && found.path("read").asBoolean() == expectedRead,
                "Notification read state was not restored for " + eventType + ": " + found);
        require(data.path("unreadCount").asInt(-1) == expectedUnreadCount,
                "Unread count was not restored for " + eventType + ".");
    }

    private static void assertExactlyTwoRequesterNotifications(String base, int acceptedId, int rejectedId)
            throws Exception {
        JsonNode data = notificationData(base, REQUESTER);
        require(data.path("notifications").size() == 2 && data.path("unreadCount").asInt(-1) == 1,
                "Invalid operation created an extra notification or changed unread count: " + data);
        assertNotificationReadState(base, REQUESTER, "EXCHANGE_ACCEPTED", acceptedId, true, 1);
        assertNotificationReadState(base, REQUESTER, "EXCHANGE_REJECTED", rejectedId, false, 1);
    }

    private static void assertEmptyNotifications(String base, String studentId) throws Exception {
        JsonNode data = notificationData(base, studentId);
        require(data.path("notifications").isArray() && data.path("notifications").isEmpty()
                        && data.path("unreadCount").asInt(-1) == 0,
                "Expected no notifications for " + studentId + ", got " + data);
    }

    private static JsonNode notificationData(String base, String studentId) throws Exception {
        String encoded = URLEncoder.encode(studentId, StandardCharsets.UTF_8);
        HttpResponse<String> response = send(base + "/api/notifications?studentId=" + encoded, null, "GET");
        assertEnvelope(response, 200, true);
        return field(response.body(), "data");
    }

    private static void assertNoNotificationFile(Path dataDirectory) {
        require(!Files.exists(dataDirectory.resolve("notifications.txt")),
                "An invalid operation unexpectedly created notifications.txt.");
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
                "Successful response has an invalid data/error value: " + body);
        else require(body.path("data").isNull() && !body.path("error").asText().isBlank(),
                "Error response has an invalid data/error value: " + body);
    }

    private static JsonNode field(String responseBody, String name) throws IOException {
        JsonNode root = JSON.readTree(responseBody);
        if (root == null || root.path("data").isMissingNode())
            throw new AssertionError("Response is missing data: " + responseBody);
        return name.equals("data") ? root.path("data") : root.path("data").path(name);
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
