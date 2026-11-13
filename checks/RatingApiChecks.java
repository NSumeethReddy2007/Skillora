import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTP checks for rating permissions, validation, portfolio output, envelopes, and restart loading. */
public final class RatingApiChecks {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(3)).build();

    public static void main(String[] args) throws Exception {
        int port = availablePort();
        Path data = Files.createTempDirectory("skillora-rating-api-check");
        String base = "http://127.0.0.1:" + port;
        Process server = startServer(port, data);
        int sessionId;
        try {
            awaitHealth(server, base);
            post(base + "/api/students", student("R-A", "Teacher", "teacher-api-rating@example.edu",
                    "Java", "Drawing", true), 201);
            post(base + "/api/students", student("R-B", "Learner", "learner-api-rating@example.edu",
                    "Java", "Drawing", false), 201);
            post(base + "/api/students", student("R-X", "Outsider", "outsider-api-rating@example.edu",
                    "Other", "Other", true), 201);

            String created = post(base + "/api/sessions", """
                    {"teacherId":"R-A","learnerId":"R-B","skillName":"Java","date":"%s","startTime":"20:00","durationMinutes":60}
                    """.formatted(LocalDate.now().plusDays(3)), 201);
            sessionId = number(created, "sessionId");
            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings", null, "GET"), 200, true);
            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    rating("R-A", 5, "Too early"), "POST"), 409, false);
            assertEnvelope(send(base + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"R-X\",\"status\":\"COMPLETED\"}", "PATCH"), 400, false);
            assertEnvelope(send(base + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"R-A\",\"status\":\"COMPLETED\"}", "PATCH"), 200, true);

            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    rating("R-A", 0, "Invalid score"), "POST"), 400, false);
            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    "{\"reviewerId\":\"R-A\",\"score\":4.7,\"feedback\":\"Fractional score\"}", "POST"), 400, false);
            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    rating("R-A", 6, "Out of range"), "POST"), 400, false);
            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    rating("R-A", 5, "   "), "POST"), 400, false);
            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    rating("R-A", 5, "x".repeat(1001)), "POST"), 400, false);
            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    rating("R-X", 5, "Outsider review"), "POST"), 400, false);
            String emptyRatings = send(base + "/api/sessions/" + sessionId + "/ratings", null, "GET").body();
            if (!emptyRatings.contains("\"data\":[]")) throw new AssertionError("Invalid submissions were stored: " + emptyRatings);

            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    rating("R-A", 5, "  Excellent lesson | clear feedback.  "), "POST"), 201, true);
            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    rating("R-A", 4, "Duplicate"), "POST"), 409, false);
            assertEnvelope(send(base + "/api/sessions/" + sessionId + "/ratings",
                    rating("R-B", 4, "Patient and encouraging."), "POST"), 201, true);
            HttpResponse<String> sessionRatings = send(base + "/api/sessions/" + sessionId + "/ratings", null, "GET");
            assertEnvelope(sessionRatings, 200, true);
            if (occurrences(sessionRatings.body(), "\"score\"") != 2
                    || !sessionRatings.body().contains("\"subject\":{\"id\":\"R-B\""))
                throw new AssertionError("Both reciprocal ratings were not returned: " + sessionRatings.body());

            HttpResponse<String> learnerPortfolio = send(base + "/api/students/R-B/portfolio", null, "GET");
            assertEnvelope(learnerPortfolio, 200, true);
            String portfolio = learnerPortfolio.body();
            if (!portfolio.contains("\"ratingCount\":1") || !portfolio.contains("\"averageRating\":5.0")
                    || !portfolio.contains("Excellent lesson | clear feedback.")
                    || !portfolio.contains("COMPLETED_SESSIONS_1")
                    || !portfolio.contains("\"completedSessions\":[") || portfolio.contains("email"))
                throw new AssertionError("Portfolio output is incomplete or exposes email: " + portfolio);
            HttpResponse<String> teacherPortfolio = send(base + "/api/students/R-A/portfolio", null, "GET");
            assertEnvelope(teacherPortfolio, 200, true);
            if (!teacherPortfolio.body().contains("\"averageRating\":4.0"))
                throw new AssertionError("Teacher portfolio did not use received ratings: " + teacherPortfolio.body());
            assertEnvelope(send(base + "/api/students/not-found/portfolio", null, "GET"), 404, false);
            assertEnvelope(send(base + "/api/sessions/999/ratings", null, "GET"), 404, false);
            assertEnvelope(send(base + "/api/students/R-A",
                    student("R-A2", "Teacher", "teacher-api-rating@example.edu", "Java", "Drawing", true),
                    "PUT"), 200, true);
            HttpResponse<String> renamedRatings = send(base + "/api/sessions/" + sessionId + "/ratings", null, "GET");
            if (!renamedRatings.body().contains("\"reviewer\":{\"id\":\"R-A2\""))
                throw new AssertionError("Rating reviewer ID was not updated: " + renamedRatings.body());
            System.out.println("Rating API validation, permissions, reciprocal review, and portfolio checks passed.");
        } finally {
            stop(server);
        }

        server = startServer(port, data);
        try {
            awaitHealth(server, base);
            HttpResponse<String> restoredRatings = send(base + "/api/sessions/" + sessionId + "/ratings", null, "GET");
            assertEnvelope(restoredRatings, 200, true);
            if (occurrences(restoredRatings.body(), "\"score\"") != 2)
                throw new AssertionError("Ratings were not restored after restart: " + restoredRatings.body());
            if (!restoredRatings.body().contains("\"reviewer\":{\"id\":\"R-A2\""))
                throw new AssertionError("Renamed reviewer ID did not survive restart: " + restoredRatings.body());
            HttpResponse<String> restoredPortfolio = send(base + "/api/students/R-B/portfolio", null, "GET");
            assertEnvelope(restoredPortfolio, 200, true);
            if (!restoredPortfolio.body().contains("\"ratingCount\":1")
                    || !restoredPortfolio.body().contains("COMPLETED_SESSIONS_1"))
                throw new AssertionError("Portfolio did not retain restored rating/session data.");
            System.out.println("Rating API restart persistence check passed.");
        } finally {
            stop(server);
        }
    }

    private static String student(String id, String name, String email, String teach, String learn, boolean teacher) {
        return """
                {"id":"%s","name":"%s","email":"%s","department":"CS","yearOfStudy":2,
                 "skillsToTeach":[{"name":"%s","category":"Technology"}],
                 "skillsToLearn":[{"name":"%s","category":"Technology"}]}
                """.formatted(id, name, email, teacher ? teach : learn, teacher ? learn : teach);
    }

    private static String rating(String reviewerId, int score, String feedback) throws Exception {
        String encodedFeedback = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(feedback);
        return "{\"reviewerId\":\"" + reviewerId + "\",\"score\":" + score
                + ",\"feedback\":" + encodedFeedback + "}";
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

    private static String post(String url, String body, int status) throws Exception {
        HttpResponse<String> response = send(url, body, "POST");
        assertEnvelope(response, status, status < 400);
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

    private static void assertEnvelope(HttpResponse<String> response, int expectedStatus, boolean success) {
        String body = response.body();
        if (response.statusCode() != expectedStatus || !body.contains("\"success\":" + success)
                || !body.contains("\"data\":") || !body.contains("\"error\":"))
            throw new AssertionError("Unexpected response/envelope: HTTP " + response.statusCode() + " " + body);
    }

    private static int number(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + field + "\\\"\\s*:\\s*(\\d+)").matcher(json);
        if (!matcher.find()) throw new AssertionError("Missing " + field + " in " + json);
        return Integer.parseInt(matcher.group(1));
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
        for (int attempt = 0; attempt < 60; attempt++) {
            if (!server.isAlive()) throw new AssertionError("API server exited before startup.");
            try {
                HttpResponse<String> response = send(base + "/api/health", null, "GET");
                if (response.statusCode() == 200) return;
            } catch (IOException ignored) { Thread.sleep(100); }
        }
        throw new AssertionError("API server did not become ready.");
    }

    private static void stop(Process process) throws InterruptedException {
        process.destroyForcibly();
        process.waitFor();
    }
}
