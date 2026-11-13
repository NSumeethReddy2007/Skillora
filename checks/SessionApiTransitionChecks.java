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

/** HTTP integration check for session state-transition status codes. */
public final class SessionApiTransitionChecks {
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        int port = availablePort();
        Path dataDirectory = Files.createTempDirectory("skillora-session-api-check");
        Path webDirectory = Path.of("web").toAbsolutePath().normalize();
        String baseUrl = "http://127.0.0.1:" + port;
        String classPath = System.getProperty("java.class.path");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();

        Process server = new ProcessBuilder(
                javaExecutable,
                "--add-modules", "jdk.httpserver",
                "-Dskillora.port=" + port,
                "-Dskillora.dataDir=" + dataDirectory,
                "-Dskillora.webDir=" + webDirectory,
                "-cp", classPath,
                "api.ApiServer")
                .directory(Path.of(".").toAbsolutePath().normalize().toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

        try {
            awaitHealth(server, baseUrl);
            post(baseUrl + "/api/students", """
                    {"id":"T-TRANSITION","name":"Teacher","email":"teacher-transition@example.edu",
                     "department":"CS","yearOfStudy":2,
                     "skillsToTeach":[{"name":"Java","category":"Technology"}]}
                    """, 201);
            post(baseUrl + "/api/students", """
                    {"id":"L-TRANSITION","name":"Learner","email":"learner-transition@example.edu",
                     "department":"Arts","yearOfStudy":1,
                     "skillsToLearn":[{"name":"Java","category":"Technology"}]}
                    """, 201);

            String createdBody = post(baseUrl + "/api/sessions", """
                    {"teacherId":"T-TRANSITION","learnerId":"L-TRANSITION","skillName":"Java",
                     "date":"%s","startTime":"23:59","durationMinutes":45}
                    """.formatted(LocalDate.now().plusDays(2)), 201);
            Matcher idMatch = Pattern.compile("\\\"sessionId\\\"\\s*:\\s*(\\d+)").matcher(createdBody);
            if (!idMatch.find()) throw new AssertionError("Create response did not contain a session ID.");
            String sessionId = idMatch.group(1);

            post(baseUrl + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"L-TRANSITION\",\"status\":\"COMPLETED\"}", 200, "PATCH");
            HttpResponse<String> invalidTransition = send(baseUrl + "/api/sessions/" + sessionId,
                    "{\"participantId\":\"T-TRANSITION\",\"status\":\"CANCELLED\"}", "PATCH");
            if (invalidTransition.statusCode() != 409) {
                throw new AssertionError("Cancelling a completed session returned HTTP "
                        + invalidTransition.statusCode() + " instead of 409.");
            }
            String body = invalidTransition.body();
            if (!body.contains("\"success\":false") || !body.contains("\"data\":null")
                    || !body.contains("\"error\":")) {
                throw new AssertionError("Conflict response did not use the standard API envelope: " + body);
            }
            HttpResponse<String> unchanged = send(baseUrl + "/api/sessions/" + sessionId, null, "GET");
            if (unchanged.statusCode() != 200 || !unchanged.body().contains("\"status\":\"COMPLETED\"")) {
                throw new AssertionError("Invalid transition changed the completed session state.");
            }
            System.out.println("Session API transition check passed (HTTP 409 and standard error envelope).");
        } finally {
            server.destroyForcibly();
            server.waitFor();
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void awaitHealth(Process server, String baseUrl) throws Exception {
        for (int attempt = 0; attempt < 40; attempt++) {
            if (!server.isAlive()) throw new AssertionError("API server exited before becoming ready.");
            try {
                HttpResponse<String> response = send(baseUrl + "/api/health", null, "GET");
                if (response.statusCode() == 200) return;
            } catch (IOException exception) {
                Thread.sleep(100);
            }
        }
        throw new AssertionError("API server did not become ready.");
    }

    private static String post(String url, String body, int expectedStatus) throws Exception {
        return post(url, body, expectedStatus, "POST");
    }

    private static String post(String url, String body, int expectedStatus, String method) throws Exception {
        HttpResponse<String> response = send(url, body, method);
        if (response.statusCode() != expectedStatus) {
            throw new AssertionError(method + " " + url + " returned HTTP " + response.statusCode()
                    + ": " + response.body());
        }
        return response.body();
    }

    private static HttpResponse<String> send(String url, String body, String method) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json");
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
