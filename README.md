# CampusSkillExchange

Java OOP mini project and Skillora web frontend for the campus skill exchange platform.

## Requirements

- JDK 25
- Maven 3.9 or newer
- Internet access on the first Maven build so Jackson can be resolved

The API uses the JDK `jdk.httpserver` module and Jackson Databind for JSON. Maven downloads Jackson; no manual JAR setup is required.

## Run the Java HTTP API and Skillora

From the repository root in Windows PowerShell:

```powershell
mvn clean compile
mvn exec:java "-Dexec.mainClass=api.ApiServer"
```

If `mvn` is not on `PATH`, use IntelliJ IDEA's Maven tool window or its bundled `mvn.cmd` from the IDE's `plugins\maven-plugin\lib\maven3\bin` directory.

Then open <http://127.0.0.1:8080/>. The server binds to loopback only and serves the existing `web/` directory and `/api/` from the same origin. Override the port with `-Dskillora.port=8081`.

The API reads and writes `data\students.txt`, `data\requests.txt`, `data\sessions.txt`, `data\notifications.txt`, and `data\ratings.txt` through `FileManager`. Missing files are handled by the existing loader and created on the first successful save. To use an alternate data directory for local testing, set `-Dskillora.dataDir=C:\path\to\data`.

## API endpoints

Successful responses use `{ "success": true, "data": ... }`; errors use `{ "success": false, "data": null, "error": "..." }`.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Health check |
| GET, POST | `/api/students` | List or register profiles |
| GET, PUT, DELETE | `/api/students/{id}` | Read, update, or delete a profile |
| POST | `/api/students/{id}/skills` | Add a `teach` or `learn` skill |
| GET | `/api/skills?name=&category=` | Search offered skills; no filters lists all offers |
| GET | `/api/skills/matches?studentId=&name=&category=` | Match skills the selected student wants to learn |
| GET, POST | `/api/exchanges` | List or create exchange requests |
| PATCH | `/api/exchanges/{requestId}` | Accept or reject a pending request as its recipient |
| GET | `/api/dashboard` | Student, offered-skill, pending-request, and accepted-request counts |
| GET, POST | `/api/sessions` | List sessions (optional `participantId` and `status` filters) or schedule a session |
| GET, PUT, PATCH | `/api/sessions/{id}` | Read, reschedule, or change a scheduled session to `COMPLETED` or `CANCELLED` |
| GET | `/api/notifications?studentId={id}` | List a registered student's in-app notifications and unread count |
| PATCH | `/api/notifications/{id}` | Mark a notification as read; JSON body supplies `studentId` |
| GET, POST | `/api/sessions/{id}/ratings` | List session ratings or submit one as a participant after completion |
| GET | `/api/students/{id}/portfolio` | Build a public portfolio from skills, completed sessions, received feedback, and milestones |

Example PowerShell requests:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8080/api/students
Invoke-RestMethod "http://127.0.0.1:8080/api/skills?category=Technology"
Invoke-RestMethod http://127.0.0.1:8080/api/dashboard
```

Write endpoints accept JSON. A student object uses `id`, `name`, `email`, `department`, `yearOfStudy`, and optional `skillsToTeach` / `skillsToLearn` arrays of `{ "name": "...", "category": "..." }`. Exchange creation accepts `requesterId`, `recipientId`, `offeredSkillName`, and `requestedSkillName`. Status updates accept `recipientId` and `status` (`ACCEPTED` or `REJECTED`). Session creation accepts `teacherId`, `learnerId`, `skillName`, `date` (`YYYY-MM-DD`), `startTime` (`HH:MM`), and `durationMinutes`; the teacher must offer the skill and the learner must list it under skills to learn. Rescheduling accepts the schedule fields and `participantId`; status changes accept `participantId` and `status` (`COMPLETED` or `CANCELLED`).

Rating submissions to `/api/sessions/{id}/ratings` accept `reviewerId`, an integer `score` from 1 to 5, and required `feedback` of at most 1,000 characters. Only completed-session participants may rate, and each participant may submit one rating per session. The reviewed participant is derived by the API. Portfolios omit email addresses and award completed-session milestones at 1, 5, and 10 sessions.

**Security limitation:** Reviewer IDs supplied by API callers are not authenticated. Participant membership checks prevent unrelated IDs from rating, but callers can impersonate a session participant until authentication and authorization are implemented.

## Run the Swing application

The existing Swing application remains available:

```powershell
mvn exec:java "-Dexec.mainClass=Main"
```

Do not run the Swing application and HTTP API simultaneously against the same data directory: each process loads an in-memory snapshot, so concurrent saves can overwrite newer changes. The HTTP server is intended for local development and has no user authentication. Session actions check the supplied participant ID against the session, but callers can claim another student's ID; this is not authentication.

## Frontend-only preview

Open `web/index.html` in a browser or use VS Code Live Server. Outside the Java server, Skillora runs with clearly identified, temporary demo data.
