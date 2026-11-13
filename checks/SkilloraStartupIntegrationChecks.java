import model.ExchangeRequest;
import model.Skill;
import model.Student;
import service.ExchangeRequestManager;
import service.StudentManager;
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
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** HTTP integration check for isolated startup, legacy data, and incremental Module 8-10 persistence. */
public final class SkilloraStartupIntegrationChecks {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(3)).build();
    private static final String TEACHER_ID = "LEGACY-TEACHER";
    private static final String LEARNER_ID = "LEGACY-LEARNER";

    public static void main(String[] args) throws Exception {
        Path dataDirectory = Files.createTempDirectory("skillora-startup-integration-");
        Process api = null;
        try {
            seedLegacyData(dataDirectory);
            assertLegacyFilesOnly(dataDirectory);

            int port = availablePort();
            String base = "http://127.0.0.1:" + port;
            api = startApi(port, dataDirectory);
            awaitHealth(api, base);

            assertEnvelope(send(base + "/api/health", null, "GET"), 200, true);
            HttpResponse<String> students = send(base + "/api/students", null, "GET");
            assertEnvelope(students, 200, true);
            require(students.body().contains(TEACHER_ID) && students.body().contains(LEARNER_ID),
                    "Legacy student records were not loaded.");

            HttpResponse<String> dashboard = send(base + "/api/dashboard", null, "GET");
            assertEnvelope(dashboard, 200, true);
            require(dashboard.body().contains("\"totalStudents\":2")
                            && dashboard.body().contains("\"pendingRequests\":1"),
                    "Dashboard did not reflect the legacy records: " + dashboard.body());

            HttpResponse<String> discovery = send(
                    base + "/api/skills?name=Java&category=Technology", null, "GET");
            assertEnvelope(discovery, 200, true);
            require(discovery.body().contains(TEACHER_ID) && discovery.body().contains("Java"),
                    "Skill discovery did not return the legacy teacher offer.");

            HttpResponse<String> matches = send(base + "/api/skills/matches?studentId=" + LEARNER_ID
                    + "&name=Java&category=Technology", null, "GET");
            assertEnvelope(matches, 200, true);
            require(matches.body().contains(TEACHER_ID) && matches.body().contains("Java"),
                    "Matching did not use the legacy learner and teacher skills.");

            HttpResponse<String> exchanges = send(base + "/api/exchanges", null, "GET");
            assertEnvelope(exchanges, 200, true);
            require(exchanges.body().contains("\"requestId\":1")
                            && exchanges.body().contains("PENDING"),
                    "The seeded legacy exchange request was not loaded.");

            assertLegacyFilesOnly(dataDirectory);
            assertOptionalFilesAbsent(dataDirectory);
            byte[] originalStudents = Files.readAllBytes(dataDirectory.resolve("students.txt"));
            byte[] originalRequests = Files.readAllBytes(dataDirectory.resolve("requests.txt"));

            HttpResponse<String> sessionCreation = send(base + "/api/sessions", """
                    {"teacherId":"LEGACY-TEACHER","learnerId":"LEGACY-LEARNER",
                     "skillName":"Java","date":"%s","startTime":"21:30","durationMinutes":60}
                    """.formatted(LocalDate.now().plusDays(3)), "POST");
            assertEnvelope(sessionCreation, 201, true);
            String created = sessionCreation.body();
            int sessionId = number(created, "sessionId");
            require(Files.exists(dataDirectory.resolve("sessions.txt")),
                    "Session creation did not create sessions.txt.");
            require(Files.exists(dataDirectory.resolve("notifications.txt")),
                    "Session creation did not persist its in-app notification.");
            require(!Files.exists(dataDirectory.resolve("ratings.txt")),
                    "ratings.txt was created before a rating was submitted.");
            assertFileFormat(dataDirectory.resolve("sessions.txt"), "L|");
            assertFileFormat(dataDirectory.resolve("notifications.txt"), "N|");

            HttpResponse<String> completion = send(base + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"" + LEARNER_ID + "\",\"status\":\"COMPLETED\"}", "PATCH");
            assertEnvelope(completion, 200, true);
            require(!Files.exists(dataDirectory.resolve("ratings.txt")),
                    "ratings.txt was created without a rating operation.");

            HttpResponse<String> rating = send(base + "/api/sessions/" + sessionId + "/ratings",
                    "{\"reviewerId\":\"" + TEACHER_ID
                            + "\",\"score\":5,\"feedback\":\"Legacy startup integration review\"}", "POST");
            assertEnvelope(rating, 201, true);
            require(Files.exists(dataDirectory.resolve("ratings.txt")),
                    "Rating submission did not create ratings.txt.");
            assertFileFormat(dataDirectory.resolve("ratings.txt"), "V|");
            assertBytesEqual(originalStudents, Files.readAllBytes(dataDirectory.resolve("students.txt")),
                    "students.txt");
            assertBytesEqual(originalRequests, Files.readAllBytes(dataDirectory.resolve("requests.txt")),
                    "requests.txt");

            stopApi(api);
            api = null;
            int restartPort = availablePort();
            String restartedBase = "http://127.0.0.1:" + restartPort;
            api = startApi(restartPort, dataDirectory);
            awaitHealth(api, restartedBase);
            verifyRestoredRecords(restartedBase, sessionId);
            assertBytesEqual(originalStudents, Files.readAllBytes(dataDirectory.resolve("students.txt")),
                    "students.txt after restart");
            assertBytesEqual(originalRequests, Files.readAllBytes(dataDirectory.resolve("requests.txt")),
                    "requests.txt after restart");

            System.out.println("Skillora isolated startup and legacy data integration checks passed.");
        } finally {
            try {
                if (api != null) stopApi(api);
            } finally {
                deleteRecursively(dataDirectory);
            }
        }
    }

    private static void seedLegacyData(Path directory) throws IOException {
        StudentManager students = new StudentManager();
        Student teacher = new Student(TEACHER_ID, "Legacy Teacher", "teacher@example.test", "Computing", 2);
        teacher.addSkillToTeach(new Skill("Java", "Technology"));
        teacher.addSkillToLearn(new Skill("Drawing", "Creative"));
        Student learner = new Student(LEARNER_ID, "Legacy Learner", "learner@example.test", "Arts", 1);
        learner.addSkillToTeach(new Skill("Drawing", "Creative"));
        learner.addSkillToLearn(new Skill("Java", "Technology"));
        students.registerStudent(teacher);
        students.registerStudent(learner);

        ExchangeRequestManager requests = new ExchangeRequestManager(students);
        ExchangeRequest request = requests.createRequest(TEACHER_ID, LEARNER_ID, "Java", "Drawing");
        require(request.getRequestId() == 1, "Unexpected legacy request ID.");

        // The legacy two-file overload writes only the pre-Module-8 students and requests formats.
        new FileManager(directory).saveData(students.getAllStudents(), requests.getAllRequests());
    }

    private static void verifyRestoredRecords(String base, int sessionId) throws Exception {
        HttpResponse<String> students = send(base + "/api/students", null, "GET");
        assertEnvelope(students, 200, true);
        require(students.body().contains(TEACHER_ID) && students.body().contains(LEARNER_ID),
                "Legacy students were not present after restart.");

        HttpResponse<String> exchanges = send(base + "/api/exchanges", null, "GET");
        assertEnvelope(exchanges, 200, true);
        require(exchanges.body().contains("\"requestId\":1") && exchanges.body().contains("PENDING"),
                "Legacy exchange request did not survive restart.");

        HttpResponse<String> session = send(base + "/api/sessions/" + sessionId, null, "GET");
        assertEnvelope(session, 200, true);
        require(session.body().contains("COMPLETED"), "Session status did not load after restart.");

        HttpResponse<String> ratings = send(base + "/api/sessions/" + sessionId + "/ratings", null, "GET");
        assertEnvelope(ratings, 200, true);
        require(ratings.body().contains("Legacy startup integration review")
                        && ratings.body().contains(TEACHER_ID),
                "Rating record did not load after restart.");

        HttpResponse<String> teacherNotifications = send(
                base + "/api/notifications?studentId=" + TEACHER_ID, null, "GET");
        assertEnvelope(teacherNotifications, 200, true);
        require(teacherNotifications.body().contains("SESSION_COMPLETED"),
                "Completed-session notification did not load for its recipient.");
        HttpResponse<String> learnerNotifications = send(
                base + "/api/notifications?studentId=" + LEARNER_ID, null, "GET");
        assertEnvelope(learnerNotifications, 200, true);
        require(learnerNotifications.body().contains("SESSION_CREATED"),
                "Session-created notification did not load for its recipient.");

        HttpResponse<String> portfolio = send(base + "/api/students/" + LEARNER_ID + "/portfolio", null, "GET");
        assertEnvelope(portfolio, 200, true);
        require(portfolio.body().contains("\"ratingCount\":1")
                        && portfolio.body().contains("\"completedSessions\":[")
                        && !portfolio.body().contains("email"),
                "Portfolio did not reflect the restored Module 8-10 data or exposed private fields.");
    }

    private static void assertLegacyFilesOnly(Path directory) throws IOException {
        require(Files.exists(directory.resolve("students.txt")), "students.txt was not seeded.");
        require(Files.exists(directory.resolve("requests.txt")), "requests.txt was not seeded.");
        assertFileFormat(directory.resolve("students.txt"), "S|");
        assertFileFormat(directory.resolve("requests.txt"), "R|");
    }

    private static void assertOptionalFilesAbsent(Path directory) {
        for (String name : List.of("sessions.txt", "ratings.txt", "notifications.txt"))
            require(!Files.exists(directory.resolve(name)), name + " should be absent before its operation.");
    }

    private static void assertFileFormat(Path file, String recordPrefix) throws IOException {
        List<String> records = Files.readAllLines(file);
        require(!records.isEmpty() && records.stream().allMatch(line -> line.startsWith(recordPrefix)),
                "Unexpected record format in " + file.getFileName() + ".");
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
            if (!server.isAlive()) throw new AssertionError("API server exited during startup.");
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

    private static HttpResponse<String> send(String url, String body, String method) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(5)).header("Accept", "application/json");
        if (body == null) request.method(method, HttpRequest.BodyPublishers.noBody());
        else request.header("Content-Type", "application/json").method(method,
                HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertEnvelope(HttpResponse<String> response, int expectedStatus, boolean success) {
        if (response.statusCode() != expectedStatus || !response.body().contains("\"success\":" + success)
                || !response.body().contains("\"data\":") || !response.body().contains("\"error\":"))
            throw new AssertionError("Unexpected API response: HTTP " + response.statusCode() + " " + response.body());
    }

    private static int number(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing " + field + " in API response: " + json);
        return Integer.parseInt(matcher.group(1));
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

    private static void assertBytesEqual(byte[] expected, byte[] actual, String fileName) {
        require(java.util.Arrays.equals(expected, actual), fileName + " was changed unexpectedly.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
