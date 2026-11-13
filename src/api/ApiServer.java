package api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.ExchangeRequest;
import model.Skill;
import model.Student;
import model.Session;
import model.Notification;
import model.Rating;
import service.ExchangeRequestManager;
import service.SkillMatcher;
import service.StudentManager;
import service.SessionManager;
import service.NotificationManager;
import service.RatingManager;
import service.PortfolioManager;
import storage.FileManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.LocalDate;
import java.time.LocalTime;

public final class ApiServer {
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_BODY_BYTES = 1_048_576;

    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final StudentManager studentManager = new StudentManager();
    private final SkillMatcher skillMatcher = new SkillMatcher();
    private final ExchangeRequestManager exchangeRequestManager =
            new ExchangeRequestManager(studentManager);
    private final SessionManager sessionManager = new SessionManager(studentManager);
    private final NotificationManager notificationManager = new NotificationManager(studentManager);
    private final RatingManager ratingManager = new RatingManager(studentManager, sessionManager);
    private final PortfolioManager portfolioManager =
            new PortfolioManager(studentManager, sessionManager, ratingManager);
    private final FileManager fileManager;
    private final Object stateLock = new Object();
    private final Path webRoot;
    private final HttpServer server;
    private final ExecutorService executor;

    private ApiServer(int port) throws IOException {
        this(port, new FileManager(Path.of(System.getProperty(
                "skillora.dataDir", Path.of(System.getProperty("user.dir"), "data").toString()))));
    }

    ApiServer(int port, FileManager fileManager) throws IOException {
        if (fileManager == null) throw new IllegalArgumentException("File manager is required.");
        this.fileManager = fileManager;
        webRoot = Path.of(System.getProperty("skillora.webDir", "web"))
                .toAbsolutePath()
                .normalize();
        loadSavedData();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        executor = Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        server.setExecutor(executor);
    }

    void start() {
        server.start();
    }

    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.getInteger("skillora.port", DEFAULT_PORT);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        ApiServer app = new ApiServer(port);
        app.start();
        System.out.println("Skillora API and frontend available at http://127.0.0.1:" + port);
        System.out.println("Using data directory: " + app.fileManager.getDataDirectory());
    }

    private void loadSavedData() throws IOException {
        FileManager.LoadResult loadedData = fileManager.loadData();
        studentManager.loadStudents(loadedData.students());
        List<String> warnings = new ArrayList<>(loadedData.warnings());
        for (FileManager.SavedRequest saved : loadedData.requests()) {
            try {
                exchangeRequestManager.restoreRequest(
                        saved.requestId(),
                        saved.requesterId(),
                        saved.recipientId(),
                        saved.offeredSkillName(),
                        saved.requestedSkillName(),
                        saved.status());
            } catch (IllegalArgumentException exception) {
                warnings.add("Request #" + saved.requestId() + ": "
                        + exception.getMessage() + " Skipped.");
            }
        }
        for (FileManager.SavedSession saved : loadedData.sessions()) {
            try {
                sessionManager.restoreSession(saved.sessionId(), saved.teacherId(), saved.learnerId(),
                        saved.skillName(), saved.date(), saved.startTime(), saved.durationMinutes(), saved.status());
            } catch (IllegalArgumentException | IllegalStateException exception) {
                warnings.add("Session #" + saved.sessionId() + ": " + exception.getMessage() + " Skipped.");
            }
        }
        for (FileManager.SavedRating saved : fileManager.loadRatings()) {
            try {
                ratingManager.restoreRating(saved.sessionId(), saved.reviewerId(), saved.score(),
                        saved.feedback(), saved.createdAt());
            } catch (IllegalArgumentException | IllegalStateException | NoSuchElementException exception) {
                warnings.add("Rating for session #" + saved.sessionId() + ": "
                        + exception.getMessage() + " Skipped.");
            }
        }
        for (FileManager.SavedNotification saved : fileManager.loadNotifications()) {
            try {
                notificationManager.restoreNotification(saved.notificationId(), saved.recipientId(),
                        saved.eventType(), saved.message(), saved.createdAt(), saved.read());
            } catch (IllegalArgumentException exception) {
                warnings.add("Notification #" + saved.notificationId() + ": "
                        + exception.getMessage() + " Skipped.");
            }
        }
        warnings.forEach(warning -> System.err.println("Data warning: " + warning));
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            if (!isJsonContentType(exchange.getRequestHeaders().getFirst("Content-Type"))
                    && hasRequestBody(exchange, exchange.getRequestMethod())) {
                sendError(exchange, 415, "Content-Type must be application/json.");
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            String method = exchange.getRequestMethod();

            if ("/api/health".equals(path)) {
                if ("GET".equals(method)) {
                    sendSuccess(exchange, 200, Map.of("status", "ok"));
                } else {
                    methodNotAllowed(exchange, "GET");
                }
                return;
            }

            synchronized (stateLock) {
                routeApi(exchange, method, path);
            }
        } catch (ApiException exception) {
            sendError(exchange, exception.status, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            sendError(exchange, 400, exception.getMessage());
        } catch (JsonProcessingException exception) {
            sendError(exchange, 400, "Request body contains invalid JSON.");
        } catch (NoSuchElementException exception) {
            sendError(exchange, 404, exception.getMessage());
        } catch (IOException exception) {
            sendError(exchange, 500, "The request could not be completed: " + exception.getMessage());
        } catch (RuntimeException exception) {
            exception.printStackTrace(System.err);
            sendError(exchange, 500, "An unexpected server error occurred.");
        } finally {
            exchange.close();
        }
    }

    private void routeApi(HttpExchange exchange, String method, String path) throws IOException {
        if ("/api/dashboard".equals(path)) {
            if ("GET".equals(method)) {
                sendSuccess(exchange, 200, dashboard());
            } else {
                methodNotAllowed(exchange, "GET");
            }
            return;
        }
        if ("/api/students".equals(path)) {
            routeStudents(exchange, method);
            return;
        }
        if ("/api/skills".equals(path)) {
            if ("GET".equals(method)) {
                routeSkills(exchange);
            } else {
                methodNotAllowed(exchange, "GET");
            }
            return;
        }
        if ("/api/skills/matches".equals(path)) {
            if ("GET".equals(method)) {
                routeMatches(exchange);
            } else {
                methodNotAllowed(exchange, "GET");
            }
            return;
        }
        if ("/api/exchanges".equals(path)) {
            routeExchanges(exchange, method);
            return;
        }
        if ("/api/notifications".equals(path)) {
            routeNotifications(exchange, method);
            return;
        }
        if (path.startsWith("/api/notifications/")) {
            routeNotification(exchange, method, path.substring("/api/notifications/".length()));
            return;
        }
        if ("/api/sessions".equals(path)) {
            routeSessions(exchange, method);
            return;
        }
        if (path.startsWith("/api/sessions/")) {
            routeSession(exchange, method, path.substring("/api/sessions/".length()));
            return;
        }
        if (path.startsWith("/api/students/")) {
            routeStudent(exchange, method, path.substring("/api/students/".length()));
            return;
        }
        if (path.startsWith("/api/exchanges/")) {
            routeExchange(exchange, method, path.substring("/api/exchanges/".length()));
            return;
        }
        sendError(exchange, 404, "API endpoint was not found.");
    }

    private void routeSessions(HttpExchange exchange, String method) throws IOException {
        if ("GET".equals(method)) {
            Map<String, String> query = queryParameters(exchange);
            String participant = query.getOrDefault("participantId", "");
            String status = query.getOrDefault("status", "");
            if (!status.isBlank() && !"UPCOMING".equalsIgnoreCase(status)
                    && !List.of("SCHEDULED", "COMPLETED", "CANCELLED").contains(status.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "Session status filter must be UPCOMING, SCHEDULED, COMPLETED, or CANCELLED.");
            }
            List<Session> result = sessionManager.getAllSessions().stream()
                    .filter(s -> participant.isBlank() || s.getTeacher().getId().equalsIgnoreCase(participant)
                            || s.getLearner().getId().equalsIgnoreCase(participant))
                    .filter(s -> status.isBlank()
                            || ("UPCOMING".equalsIgnoreCase(status)
                                ? s.getStatus() == Session.Status.SCHEDULED
                                    && !s.getDate().atTime(s.getStartTime()).isBefore(java.time.LocalDateTime.now())
                                : s.getStatus().name().equalsIgnoreCase(status)))
                    .toList();
            sendSuccess(exchange, 200, result.stream().map(this::toSessionDto).toList());
            return;
        }
        if ("POST".equals(method)) {
            SessionInput input = readBody(exchange, SessionInput.class);
            Session session = sessionManager.createSession(input.teacherId(), input.learnerId(),
                    input.skillName(), parseDate(input.date()), parseTime(input.startTime()), input.durationMinutes());
            persist();
            createNotification(session.getLearner().getId(), "SESSION_CREATED",
                    "Session #" + session.getSessionId() + " was scheduled with "
                            + session.getTeacher().getName() + ".");
            sendSuccess(exchange, 201, toSessionDto(session));
            return;
        }
        methodNotAllowed(exchange, "GET, POST");
    }

    private void routeSession(HttpExchange exchange, String method, String remainder) throws IOException {
        String[] segments = remainder.split("/", -1);
        int id;
        try { id = Integer.parseInt(segments[0]); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Session ID must be a positive integer."); }
        if (id < 1 || segments.length > 2) throw new IllegalArgumentException("Session ID must be a positive integer.");
        if (segments.length == 2 && "ratings".equals(segments[1])) {
            Session session = sessionManager.getSession(id);
            if ("GET".equals(method)) {
                sendSuccess(exchange, 200, ratingManager.getRatingsForSession(id).stream()
                        .map(this::toRatingDto).toList());
                return;
            }
            if ("POST".equals(method)) {
                RatingInput input = readBody(exchange, RatingInput.class);
                if (input.score() == null || !input.score().isIntegralNumber()
                        || !input.score().canConvertToInt()) {
                    throw new IllegalArgumentException("Rating must be an integer from 1 to 5.");
                }
                Rating rating;
                try {
                    rating = ratingManager.submitRating(session.getSessionId(), input.reviewerId(),
                            input.score().intValue(), input.feedback());
                } catch (IllegalStateException exception) {
                    throw new ApiException(409, exception.getMessage());
                }
                try {
                    persistRatings();
                } catch (IOException exception) {
                    ratingManager.discardRating(rating.getSessionId(), rating.getReviewer().getId());
                    throw exception;
                }
                sendSuccess(exchange, 201, toRatingDto(rating));
                return;
            }
            methodNotAllowed(exchange, "GET, POST");
            return;
        }
        if (segments.length == 1 && "GET".equals(method)) {
            sendSuccess(exchange, 200, toSessionDto(sessionManager.getSession(id)));
            return;
        }
        if (segments.length == 1 && "PUT".equals(method)) {
            SessionUpdateInput input = readBody(exchange, SessionUpdateInput.class);
            Session session = sessionManager.getSession(id);
            String otherParticipantId = otherSessionParticipant(session, input.participantId());
            try {
                sessionManager.updateSession(id, input.participantId(), parseDate(input.date()),
                        parseTime(input.startTime()), input.durationMinutes());
            } catch (IllegalStateException exception) {
                throw new ApiException(409, exception.getMessage());
            }
            persist();
            createNotification(otherParticipantId, "SESSION_RESCHEDULED",
                    "Session #" + id + " was rescheduled to " + session.getDate() + " at "
                            + session.getStartTime() + ".");
            sendSuccess(exchange, 200, toSessionDto(session)); return;
        }
        if (segments.length == 1 && "PATCH".equals(method)) {
            SessionStatusInput input = readBody(exchange, SessionStatusInput.class);
            String status = requiredText(input.status(), "Status").toUpperCase(Locale.ROOT);
            Session session = sessionManager.getSession(id);
            String otherParticipantId = otherSessionParticipant(session, input.participantId());
            try {
                switch (status) {
                    case "CANCELLED" -> sessionManager.cancelSession(id, input.participantId());
                    case "COMPLETED" -> sessionManager.completeSession(id, input.participantId());
                    default -> throw new IllegalArgumentException("Status must be 'COMPLETED' or 'CANCELLED'.");
                }
            } catch (IllegalStateException exception) {
                throw new ApiException(409, exception.getMessage());
            }
            persist();
            createNotification(otherParticipantId, "SESSION_" + status,
                    "Session #" + id + " was " + status.toLowerCase(Locale.ROOT) + ".");
            sendSuccess(exchange, 200, toSessionDto(session)); return;
        }
        methodNotAllowed(exchange, "GET, PUT, PATCH");
    }

    private static LocalDate parseDate(String value) {
        try { return LocalDate.parse(requiredText(value, "Session date")); }
        catch (java.time.format.DateTimeParseException exception) { throw new IllegalArgumentException("Session date must use YYYY-MM-DD format."); }
    }
    private static LocalTime parseTime(String value) {
        try { return LocalTime.parse(requiredText(value, "Session start time")); }
        catch (java.time.format.DateTimeParseException exception) { throw new IllegalArgumentException("Session start time must use HH:MM format."); }
    }

    private SessionDto toSessionDto(Session session) {
        return new SessionDto(session.getSessionId(), toStudentDto(session.getTeacher()),
                toStudentDto(session.getLearner()), toSkillDto(session.getSkill()), session.getDate().toString(),
                session.getStartTime().toString(), session.getDurationMinutes(), session.getStatus().name());
    }

    private void routeStudents(HttpExchange exchange, String method) throws IOException {
        if ("GET".equals(method)) {
            sendSuccess(exchange, 200, studentManager.getAllStudents().stream()
                    .map(this::toStudentDto).toList());
            return;
        }
        if ("POST".equals(method)) {
            StudentInput input = readBody(exchange, StudentInput.class);
            Student student = createStudent(input);
            studentManager.registerStudent(student);
            persist();
            sendSuccess(exchange, 201, toStudentDto(student));
            return;
        }
        methodNotAllowed(exchange, "GET, POST");
    }

    private void routeStudent(HttpExchange exchange, String method, String remainder)
            throws IOException {
        String[] segments = remainder.split("/", -1);
        String studentId = decodePathSegment(segments[0]);
        if (studentId.isBlank()) {
            sendError(exchange, 404, "Student endpoint was not found.");
            return;
        }
        Student student = findStudent(studentId);

        if (segments.length == 1) {
            switch (method) {
                case "GET" -> sendSuccess(exchange, 200, toStudentDto(student));
                case "PUT" -> {
                    StudentInput input = readBody(exchange, StudentInput.class);
                    Student updated = createStudent(input);
                    Student previous = new Student(student.getId(), student.getName(), student.getEmail(),
                            student.getDepartment(), student.getYearOfStudy());
                    studentManager.updateStudent(studentId, updated);
                    try {
                        notificationManager.updateStudentId(studentId, updated.getId());
                        fileManager.saveStudentUpdateData(studentManager.getAllStudents(),
                                exchangeRequestManager.getAllRequests(), sessionManager.getAllSessions(),
                                ratingManager.getAllRatings(), notificationManager.getAllNotifications());
                    } catch (IOException | RuntimeException persistenceFailure) {
                        try {
                            studentManager.updateStudent(updated.getId(), previous);
                            notificationManager.updateStudentId(updated.getId(), previous.getId());
                        } catch (RuntimeException rollbackFailure) {
                            persistenceFailure.addSuppressed(rollbackFailure);
                            throw new IOException("Student update failed and in-memory rollback failed.",
                                    persistenceFailure);
                        }
                        if (persistenceFailure instanceof IOException ioFailure) throw ioFailure;
                        throw persistenceFailure;
                    }
                    sendSuccess(exchange, 200, toStudentDto(student));
                }
                case "DELETE" -> {
                    if (sessionManager.hasSessionsForStudent(studentId)) {
                        throw new ApiException(409,
                                "Student cannot be deleted while session records reference the profile.");
                    }
                    notificationManager.deleteNotificationsForStudent(studentId);
                    studentManager.deleteStudent(studentId);
                    int removedRequests = exchangeRequestManager.deleteRequestsForStudent(studentId);
                    persist();
                    persistNotifications();
                    sendSuccess(exchange, 200, Map.of(
                            "deleted", true,
                            "removedRequests", removedRequests));
                }
                default -> methodNotAllowed(exchange, "GET, PUT, DELETE");
            }
            return;
        }

        if (segments.length == 2 && "skills".equals(segments[1])) {
            if (!"POST".equals(method)) {
                methodNotAllowed(exchange, "POST");
            }
            SkillInput input = readBody(exchange, SkillInput.class);
            Skill skill = new Skill(input.name(), input.category());
            String type = requiredText(input.type(), "Skill type").toLowerCase(Locale.ROOT);
            switch (type) {
                case "teach", "can teach", "teaches" -> student.addSkillToTeach(skill);
                case "learn", "wants to learn", "learns" -> student.addSkillToLearn(skill);
                default -> throw new IllegalArgumentException(
                        "Skill type must be 'teach' or 'learn'.");
            }
            persist();
            sendSuccess(exchange, 201, toStudentDto(student));
            return;
        }
        if (segments.length == 2 && "portfolio".equals(segments[1])) {
            if (!"GET".equals(method)) {
                methodNotAllowed(exchange, "GET");
                return;
            }
            sendSuccess(exchange, 200, portfolioManager.getPortfolio(studentId));
            return;
        }
        sendError(exchange, 404, "Student endpoint was not found.");
    }

    private void routeSkills(HttpExchange exchange) throws IOException {
        Map<String, String> query = queryParameters(exchange);
        String name = query.getOrDefault("name", "");
        String category = query.getOrDefault("category", "");
        List<SkillMatcher.SkillSearchResult> results;
        if (name.isBlank() && category.isBlank()) {
            results = studentManager.getAllStudents().stream()
                    .filter(student -> !student.getSkillsToTeach().isEmpty())
                    .map(student -> new SkillMatcher.SkillSearchResult(
                            student, student.getSkillsToTeach()))
                    .toList();
        } else {
            results = skillMatcher.searchSkills(studentManager.getAllStudents(), name, category);
        }
        sendSuccess(exchange, 200, results.stream().map(this::toSearchResultDto).toList());
    }

    private void routeMatches(HttpExchange exchange) throws IOException {
        Map<String, String> query = queryParameters(exchange);
        String studentId = requiredText(query.get("studentId"), "Student ID");
        Student learner = findStudent(studentId);
        List<SkillMatcher.MatchResult> results = skillMatcher.findMatches(
                learner,
                studentManager.getAllStudents(),
                query.getOrDefault("name", ""),
                query.getOrDefault("category", ""));
        sendSuccess(exchange, 200, results.stream().map(this::toMatchResultDto).toList());
    }

    private void routeExchanges(HttpExchange exchange, String method) throws IOException {
        if ("GET".equals(method)) {
            sendSuccess(exchange, 200, exchangeRequestManager.getAllRequests().stream()
                    .map(this::toExchangeDto).toList());
            return;
        }
        if ("POST".equals(method)) {
            ExchangeInput input = readBody(exchange, ExchangeInput.class);
            ExchangeRequest request = exchangeRequestManager.createRequest(
                    input.requesterId(),
                    input.recipientId(),
                    input.offeredSkillName(),
                    input.requestedSkillName());
            persist();
            sendSuccess(exchange, 201, toExchangeDto(request));
            return;
        }
        methodNotAllowed(exchange, "GET, POST");
    }

    private void routeExchange(HttpExchange exchange, String method, String remainder)
            throws IOException {
        if (!"PATCH".equals(method)) {
            methodNotAllowed(exchange, "PATCH");
            return;
        }
        int requestId;
        try {
            requestId = Integer.parseInt(remainder);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Exchange request ID must be a positive integer.");
        }
        if (requestId < 1) {
            throw new IllegalArgumentException("Exchange request ID must be a positive integer.");
        }
        ExchangeStatusInput input = readBody(exchange, ExchangeStatusInput.class);
        String status = requiredText(input.status(), "Status").toUpperCase(Locale.ROOT);
        switch (status) {
            case "ACCEPTED" -> exchangeRequestManager.acceptRequest(requestId, input.recipientId());
            case "REJECTED" -> exchangeRequestManager.rejectRequest(requestId, input.recipientId());
            default -> throw new IllegalArgumentException(
                    "Status must be 'ACCEPTED' or 'REJECTED'.");
        }
        persist();
        ExchangeRequest updated = exchangeRequestManager.getAllRequests().stream()
                .filter(request -> request.getRequestId() == requestId)
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Exchange request was not found."));
        createNotification(updated.getRequester().getId(), "EXCHANGE_" + status,
                "Your exchange request #" + requestId + " was " + status.toLowerCase(Locale.ROOT) + ".");
        sendSuccess(exchange, 200, toExchangeDto(updated));
    }

    private void routeNotifications(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equals(method)) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        String studentId = requiredText(queryParameters(exchange).get("studentId"), "Student ID");
        List<NotificationDto> items = notificationManager.getNotificationsForStudent(studentId).stream()
                .map(this::toNotificationDto).toList();
        sendSuccess(exchange, 200, new NotificationsDto(items, notificationManager.getUnreadCount(studentId)));
    }

    private void routeNotification(HttpExchange exchange, String method, String remainder) throws IOException {
        if (!"PATCH".equals(method)) {
            methodNotAllowed(exchange, "PATCH");
            return;
        }
        int id;
        try { id = Integer.parseInt(remainder); }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Notification ID must be a positive integer.");
        }
        if (id < 1) throw new IllegalArgumentException("Notification ID must be a positive integer.");
        NotificationReadInput input = readBody(exchange, NotificationReadInput.class);
        Notification notification = notificationManager.markAsRead(id,
                requiredText(input.studentId(), "Student ID"));
        persistNotifications();
        sendSuccess(exchange, 200, toNotificationDto(notification));
    }

    private String otherSessionParticipant(Session session, String participantId) {
        String validId = requiredText(participantId, "Participant student ID");
        if (session.getTeacher().getId().equalsIgnoreCase(validId)) return session.getLearner().getId();
        if (session.getLearner().getId().equalsIgnoreCase(validId)) return session.getTeacher().getId();
        throw new IllegalArgumentException("Only a session participant can perform this action.");
    }

    private void createNotification(String recipientId, String eventType, String message) {
        Notification notification = notificationManager.createNotification(recipientId, eventType, message);
        try {
            persistNotifications();
        } catch (IOException exception) {
            notificationManager.discardNotification(notification.getNotificationId());
            System.err.println("Notification persistence failed after " + eventType
                    + "; the original operation was already saved, and the notification was rolled back: "
                    + exception.getMessage());
        }
    }

    private void persistNotifications() throws IOException {
        fileManager.saveNotifications(notificationManager.getAllNotifications());
    }

    private void persistRatings() throws IOException {
        fileManager.saveRatings(ratingManager.getAllRatings());
    }

    private Map<String, Object> dashboard() {
        List<Student> students = studentManager.getAllStudents();
        List<ExchangeRequest> requests = exchangeRequestManager.getAllRequests();
        Set<Skill> offeredSkills = new HashSet<>();
        students.forEach(student -> offeredSkills.addAll(student.getSkillsToTeach()));
        long pending = requests.stream()
                .filter(request -> request.getStatus() == ExchangeRequest.Status.PENDING)
                .count();
        long accepted = requests.stream()
                .filter(request -> request.getStatus() == ExchangeRequest.Status.ACCEPTED)
                .count();
        return Map.of(
                "totalStudents", students.size(),
                "availableSkills", offeredSkills.size(),
                "pendingRequests", pending,
                "acceptedExchanges", accepted);
    }

    private Student createStudent(StudentInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Student details must be a JSON object.");
        }
        Student student = new Student(
                input.id(), input.name(), input.email(), input.department(), input.yearOfStudy());
        addSkills(student, input.skillsToTeach(), true);
        addSkills(student, input.skillsToLearn(), false);
        return student;
    }

    private void addSkills(Student student, List<SkillInput> skills, boolean teach) {
        if (skills == null) {
            return;
        }
        for (SkillInput input : skills) {
            if (input == null) {
                throw new IllegalArgumentException("Skill details cannot be null.");
            }
            Skill skill = new Skill(input.name(), input.category());
            if (teach) {
                student.addSkillToTeach(skill);
            } else {
                student.addSkillToLearn(skill);
            }
        }
    }

    private Student findStudent(String studentId) {
        return studentManager.findStudentById(studentId)
                .orElseThrow(() -> new ApiException(404, "Student profile was not found."));
    }

    private void persist() throws IOException {
        fileManager.saveData(
                studentManager.getAllStudents(),
                exchangeRequestManager.getAllRequests(), sessionManager.getAllSessions());
    }

    private NotificationDto toNotificationDto(Notification notification) {
        return new NotificationDto(notification.getNotificationId(), notification.getRecipientId(),
                notification.getEventType(), notification.getMessage(), notification.getCreatedAt().toString(),
                notification.isRead());
    }

    private RatingDto toRatingDto(Rating rating) {
        return new RatingDto(rating.getSessionId(),
                new PersonSummaryDto(rating.getReviewer().getId(), rating.getReviewer().getName()),
                new PersonSummaryDto(rating.getSubject().getId(), rating.getSubject().getName()),
                rating.getScore(), rating.getFeedback(), rating.getCreatedAt().toString());
    }

    private StudentDto toStudentDto(Student student) {
        return new StudentDto(
                student.getId(),
                student.getName(),
                student.getEmail(),
                student.getDepartment(),
                student.getYearOfStudy(),
                student.getSkillsToTeach().stream().map(this::toSkillDto).toList(),
                student.getSkillsToLearn().stream().map(this::toSkillDto).toList());
    }

    private SkillDto toSkillDto(Skill skill) {
        return new SkillDto(skill.getName(), skill.getCategory());
    }

    private SkillResultDto toSearchResultDto(SkillMatcher.SkillSearchResult result) {
        return new SkillResultDto(
                toStudentDto(result.student()),
                result.matchingSkills().stream().map(this::toSkillDto).toList(),
                null);
    }

    private SkillResultDto toMatchResultDto(SkillMatcher.MatchResult result) {
        return new SkillResultDto(
                toStudentDto(result.student()),
                result.matchingSkills().stream().map(this::toSkillDto).toList(),
                result.matchPercentage());
    }

    private ExchangeDto toExchangeDto(ExchangeRequest request) {
        return new ExchangeDto(
                request.getRequestId(),
                toStudentDto(request.getRequester()),
                toStudentDto(request.getRecipient()),
                toSkillDto(request.getOfferedSkill()),
                toSkillDto(request.getRequestedSkill()),
                request.getStatus().name());
    }

    private <T> T readBody(HttpExchange exchange, Class<T> type) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) {
            throw new ApiException(413, "Request body must not exceed 1 MB.");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("A JSON request body is required.");
        }
        T value = json.readValue(bytes, type);
        if (value == null) {
            throw new IllegalArgumentException("Request body must be a JSON object.");
        }
        return value;
    }

    private static boolean hasRequestBody(HttpExchange exchange) {
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        return length == null || !"0".equals(length);
    }

    private static boolean hasRequestBody(HttpExchange exchange, String method) {
        return hasRequestBodyMethod(method) && hasRequestBody(exchange);
    }

    private static boolean isJsonContentType(String contentType) {
        return contentType != null
                && "application/json".equalsIgnoreCase(contentType.split(";", 2)[0].trim());
    }

    private static Map<String, String> queryParameters(HttpExchange exchange) {
        Map<String, String> values = new HashMap<>();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return values;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            values.put(key, value);
        }
        return values;
    }

    private static String decodePath(String path) {
        return URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String decodePathSegment(String segment) {
        return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String requiredText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                sendStaticError(exchange, 405, "Method not allowed.");
                return;
            }
            String path = decodePath(exchange.getRequestURI().getRawPath());
            String relative = "/".equals(path) ? "index.html" : path.substring(1);
            Path file = webRoot.resolve(relative).normalize();
            if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
                sendStaticError(exchange, 404, "Frontend file was not found.");
                return;
            }
            byte[] content = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType(file));
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Content-Length", Integer.toString(content.length));
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            }
        } catch (IllegalArgumentException exception) {
            sendStaticError(exchange, 400, "Invalid frontend path.");
        } finally {
            exchange.close();
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return switch (name.substring(name.lastIndexOf('.') + 1)) {
            case "html" -> "text/html; charset=utf-8";
            case "css" -> "text/css; charset=utf-8";
            case "js" -> "text/javascript; charset=utf-8";
            case "json" -> "application/json; charset=utf-8";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private void sendSuccess(HttpExchange exchange, int status, Object data) throws IOException {
        sendJson(exchange, status, new ApiResponse<>(true, data, null));
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, new ApiResponse<>(false, null, message));
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sendStaticError(HttpExchange exchange, int status, String message)
            throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void methodNotAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        throw new ApiException(405, "Method not allowed.");
    }

    private static boolean hasRequestBodyMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private record ApiResponse<T>(boolean success, T data, String error) {
    }

    private record StudentInput(
            String id,
            String name,
            String email,
            String department,
            int yearOfStudy,
            List<SkillInput> skillsToTeach,
            List<SkillInput> skillsToLearn) {
    }

    private record SkillInput(String name, String category, String type) {
    }

    private record ExchangeInput(
            String requesterId,
            String recipientId,
            String offeredSkillName,
            String requestedSkillName) {
    }

    private record ExchangeStatusInput(String recipientId, String status) {
    }

    private record SessionInput(String teacherId, String learnerId, String skillName,
                                String date, String startTime, int durationMinutes) { }
    private record SessionUpdateInput(String participantId, String date, String startTime,
                                      int durationMinutes) { }
    private record SessionStatusInput(String participantId, String status) { }
    private record SessionDto(int sessionId, StudentDto teacher, StudentDto learner, SkillDto skill,
                              String date, String startTime, int durationMinutes, String status) { }
    private record NotificationReadInput(String studentId) { }
    private record NotificationDto(int notificationId, String recipientId, String eventType,
                                   String message, String createdAt, boolean read) { }
    private record NotificationsDto(List<NotificationDto> notifications, int unreadCount) { }
    private record RatingInput(String reviewerId, JsonNode score, String feedback) { }
    private record PersonSummaryDto(String id, String name) { }
    private record RatingDto(int sessionId, PersonSummaryDto reviewer, PersonSummaryDto subject,
                             int score, String feedback, String createdAt) { }

    private record SkillDto(String name, String category) {
    }

    private record StudentDto(
            String id,
            String name,
            String email,
            String department,
            int yearOfStudy,
            List<SkillDto> skillsToTeach,
            List<SkillDto> skillsToLearn) {
    }

    private record SkillResultDto(
            StudentDto student,
            List<SkillDto> matchingSkills,
            Double matchPercentage) {
    }

    private record ExchangeDto(
            int requestId,
            StudentDto requester,
            StudentDto recipient,
            SkillDto offeredSkill,
            SkillDto requestedSkill,
            String status) {
    }

    private static final class ApiException extends RuntimeException {
        private final int status;

        private ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }
}
