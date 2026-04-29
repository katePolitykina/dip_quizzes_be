# Authentication Module Report

## Security Configuration

The `SecurityFilterChain` exposes `/api/auth/**` publicly for registration, login, guest token issuance, and future OAuth entry points. A custom `JwtAuthenticationFilter` reads bearer tokens, reconstructs the authenticated principal, and maps the JWT `role` claim into Spring Security authorities.

Access to `/api/users/me` is restricted with `hasRole("USER")`, so only persisted local or Google-backed accounts can access profile management. Guest tokens are issued with `ROLE_GUEST`; they authenticate successfully but fail authorization on user-only endpoints.

## Virtual Threads

`application.yml` enables virtual threads:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

## Guest Handling

`POST /api/auth/guest` accepts a nickname and optional avatar URL, generates a transient UUID, and returns a short-lived JWT with `ROLE_GUEST`. No guest entity is written to PostgreSQL; the temporary identity exists only inside the token claims for the lifetime of that session token.

## Database Schema

The permanent `users` table is backed by the `User` JPA entity with:

- `id` as a UUID primary key
- `email` as a unique nullable column
- `password` as a non-null BCrypt-hashed credential
- `display_name` mapped from `displayName`, required and limited to 50 characters
- `avatar_url` as an optional profile image
- `provider` as an enum (`LOCAL`, `GOOGLE`)

# Quiz Administration & Management Report

## ER-Diagram Description

`User` has a one-to-many relationship with `Quiz` through `Quiz.author`. Each quiz belongs to exactly one persisted user author.

`Quiz` has a one-to-many relationship with `Question`. Questions are stored with `quiz_id` and are cascaded with orphan removal, so a full quiz update replaces the persisted question set safely.

`Question` has a one-to-many relationship with `Answer`. Answers are stored with `question_id` and are also cascaded with orphan removal, so deleting or replacing a question removes its child answers automatically.

## Validation Logic

Field-level validation uses Jakarta Validation on request DTOs:

- quiz `title` must be 3-100 characters
- quiz `questions` must be present and non-empty
- question `text` must be non-blank
- question `pointsWeight` must be present and at least 1
- answer `text` must be non-blank
- answer `isCorrect` must be present

The structural rules are enforced in `QuizService.validateQuestionStructure(...)` before persistence:

- each question must contain between 2 and 4 answers
- each question must contain at least one answer where `isCorrect = true`

If a request violates either rule, the service throws a `400 Bad Request` with a precise validation message and blocks saving/updating.

## Ownership Check

Quiz ownership is enforced in the service layer by loading quizzes with `quizRepository.findByIdAndAuthorId(quizId, authenticatedUser.id())`. If no row matches both the quiz ID and the current authenticated user ID, the API returns `404 Quiz not found`.

This ensures a user can only view, update, or delete quizzes they authored, even if they know another quiz UUID.

## API Sample

Example `POST /api/quizzes` request body:

```json
{
  "title": "European Capitals",
  "questions": [
    {
      "text": "What is the capital of France?",
      "pointsWeight": 100,
      "timerOverride": 30,
      "answers": [
        { "text": "Paris", "isCorrect": true },
        { "text": "Berlin", "isCorrect": false },
        { "text": "Madrid", "isCorrect": false }
      ]
    }
  ]
}
```

# Lobby Initialization & Team Formation Report

## Redis Key Strategy

Each room is stored as a single Redis value under `game-session:{PIN}`. The value is a serialized `GameSession` object containing:

- host metadata: `hostUserId`
- lobby config: `globalTimer`, `cbmEnabled`, `status`
- timestamps: `createdAt`, `updatedAt`
- connected participants: display/profile data plus assigned team and role
- team state: team IDs, member lists, captain ID, and analyst ID

Room mutation locking uses a separate short-lived key: `game-session-lock:{PIN}`.

## PIN Algorithm

Room PINs are generated as 6-character uppercase alphanumeric codes from a restricted alphabet to avoid ambiguous characters. Creation loops until `GameSessionStore.createIfAbsent(...)` succeeds.

In Redis mode, collision prevention is handled atomically with `SETNX` semantics through `setIfAbsent(...)`. If a generated PIN already exists, the service discards it and generates another one.

## Auto-Distribution Logic

Players are sorted by join order and assigned to teams in round-robin order. That keeps team sizes balanced so the size difference between any two teams is at most one.

After distribution, the first member in each team becomes `CAPTAIN`, the second becomes `ANALYST`, and all remaining members stay `MEMBER`. The service blocks distribution unless there are at least two players per team, which guarantees every team can receive both required roles.

## Concurrency Handling

Two hosts cannot accidentally receive the same PIN because room creation uses an atomic create-if-absent operation in Redis. Even if two requests generate the same candidate PIN at the same moment, only one write succeeds.

Two players cannot grab the same role during manual updates because room mutations are executed inside a room-scoped lock (`game-session-lock:{PIN}`). Join, auto-distribution, and manual role reassignment all run through that lock, so each update sees a stable room state before saving the next version.

# Real-Time Communication Report

## Topic Hierarchy

The STOMP broker uses `/app` for incoming client messages and `/topic` for broadcasts.

- `/topic/rooms/{pin}` carries room-wide events wrapped in a socket envelope:
  - `ROOM_STATE` with the latest `GameSessionResponse`
  - `GAME_PAUSED` / `GAME_RESUMED` with the updated room state
  - `PLAYER_KICKED` with the updated room state
- `/topic/rooms/{pin}/teams/{teamId}` carries team-only sync events:
  - `TEAM_SELECTION_UPDATED` with the currently selected answer
  - `TEAM_ANSWER_CONFIRMED` with the finalized answer

Incoming message destinations are:

- `/app/rooms/{pin}/moderation`
- `/app/rooms/{pin}/teams/{teamId}/selection`
- `/app/rooms/{pin}/teams/{teamId}/confirm`

## Authentication Flow

During the STOMP `CONNECT` frame, the client must send `Authorization: Bearer <jwt>` as a native header. `JwtStompChannelInterceptor` extracts the token, validates it through `JwtService`, and converts the resulting `AuthenticatedUser` into a Spring `Authentication` object.

That authenticated principal is then attached to the WebSocket session and reused for later `SUBSCRIBE` and `SEND` frames. The interceptor also records the STOMP session ID so later moderation actions can target the participant’s live socket connection.

## Pause Logic

When the host sends `TOGGLE_PAUSE` to `/app/rooms/{pin}/moderation`, the server updates the room status inside the room lock, persists the new state, and immediately broadcasts both:

- a `ROOM_STATE` update to `/topic/rooms/{pin}`
- a `GAME_PAUSED` or `GAME_RESUMED` event to the same topic

This gives clients an immediate broadcast signal to freeze or resume local timers without waiting for polling or a later refresh cycle.

## Role Enforcement

Team answer confirmation is handled server-side in `GameLoopService.confirmTeamAnswer(...)`. Before accepting a confirm action, the service:

- loads the room and team from Redis
- verifies the sender is a participant of that team
- checks that the sender’s current `teamRole` is `CAPTAIN`

If the sender is not the captain, the server rejects the action with `403 Forbidden`. Only valid captain confirmations update `confirmedAnswerId` and trigger the `TEAM_ANSWER_CONFIRMED` broadcast.

# Game Engine & Scoring Report

## CBM Matrix

| Confidence | Correct Multiplier | Incorrect Penalty |
| --- | ---: | ---: |
| High | `x2.0` | `-1.0 * baseWeight` |
| Medium | `x1.5` | `-0.5 * baseWeight` |
| Low | `x1.0` | `0` |

These defaults are stored in the session as CBM settings and may be overridden by the host in the `START_GAME` payload before the first question begins.

## Scoring Algorithm

The scoring utility calculates:

`Points = BaseWeight * SpeedFactor * ConfidenceMultiplier`

For correct answers, the speed bonus is based on the percentage of question time remaining when the captain confirms:

- `remainingRatio = remainingMillis / totalQuestionMillis`
- `speedFactor = 1.0 + remainingRatio`

That means a team answering instantly gets close to a `2.0` speed factor, while a team answering at the deadline gets `1.0`. Incorrect answers use a speed factor of `1.0`, so the configured CBM penalty applies directly without extra time distortion.

## Analyst Logic

The 50/50 action is tracked in Redis with `team.analystPowerUsed`. When an analyst sends the `SORT_ANSWERS` action:

- the server verifies the sender currently has the `ANALYST` role
- the server verifies the team has not spent the ability already
- the server stores `analystPowerUsed = true`
- the server returns two incorrect answer IDs to the team-only topic

Any second use in the same game is rejected with `409 Conflict`.

## State Diagram

The runtime flow is:

1. Host creates a room and distributes teams in `LOBBY`.
2. Host sends `START_GAME` with the selected quiz and optional CBM overrides.
3. The server snapshots quiz questions into Redis and transitions to `START_QUESTION`.
4. During the active question:
   - teammates sync answer selection in real time
   - the captain confirms the final answer with a confidence level
   - the server updates the live answer histogram
   - the question ends when all teams confirm or the scheduled timer expires
5. The server scores the question, recalculates rankings, broadcasts the leaderboard, and transitions to `SHOW_RESULTS`.
6. If more questions remain, the host sends `ADVANCE` and the server transitions back to `START_QUESTION`.
7. After the last question is scored, the server transitions to `FINISHED`, builds the final per-team scoring report, and broadcasts final results.

# Frontend Integration Report

## Connection Lifecycle

The React SPA now uses a centralized `apiClient` for all REST calls. The client reads the persisted JWT session from local storage, attaches `Authorization: Bearer <token>` to every authenticated request, and triggers a shared unauthorized handler when the token is missing, expired, or rejected with `401`.

That handler clears the Redux auth state, clears the active room state, disconnects the WebSocket client, and returns the UI to the dashboard. This prevents the app from continuing with stale credentials after either REST or STOMP authentication failure.

Google sign-in now follows the same JWT-based lifecycle. Spring Security completes the Google OAuth handshake on the backend, creates or updates the matching user, issues the standard application JWT, and redirects to the SPA callback route with the serialized auth payload. The frontend callback stores that session in the same auth store used by email/password login, then loads `/api/users/me` and the quiz library.

The room socket is managed by a dedicated `RoomSocketClient` that sends the JWT in the STOMP `CONNECT` frame as a native `Authorization` header. When the socket drops unexpectedly, the client reconnects with exponential backoff up to 5 seconds and re-subscribes to:

- `/topic/rooms/{pin}`
- `/topic/rooms/{pin}/leaderboard`
- `/topic/rooms/{pin}/teams/{teamId}` once the player’s team assignment is known

Intentional disconnects, such as leaving a room or logging out, disable the reconnect loop so the client does not reopen a room after the user has navigated away.

## Role-Based UI

The SPA now differentiates the live game screen by resolved backend role:

- `CAPTAIN`: sees the `Confirm` action and can finalize the team answer
- `ANALYST`: sees the `50/50` action, which sends `/app/rooms/{pin}/teams/{teamId}/sort-answers`
- `MEMBER`: can select answers for team discussion but does not see captain or analyst control buttons

The lobby also reflects backend roles from `ROOM_STATE`. Team cards highlight captain and analyst assignments directly from `teamRole`, and the host-only lobby controls now use real room actions instead of simulated drag state.

Examples:

- A captain selects an answer, receives the CBM confidence overlay when CBM is enabled, and only then sends the confirm frame.
- An analyst sees the `50/50` button until `analystPowerUsed` becomes true, after which the button is disabled.
- A regular member sees the shared selection state update in real time but never sees moderation or confirm controls.

## Latency Management

The backend remains the source of truth, but the frontend reduces perceived lag for the 100-200ms team-sync target by combining three mechanisms:

- immediate socket `SEND` on answer selection, without debounce
- room-wide `ROOM_STATE` updates for authoritative reconciliation
- team-topic subscriptions for fast team-local events such as `TEAM_SELECTION_UPDATED`, `TEAM_ANSWER_CONFIRMED`, and `ANALYST_SORT_ANSWERS`

The question timer is rendered from the server-provided deadline and refreshed locally every 200ms, which keeps the displayed countdown smooth between broadcasts while still respecting the authoritative server schedule. Pause and resume events replace local assumptions with the latest server room state so all clients converge after moderation events.

## Analytics & Results

The host view now consumes the live answer-progress payload from `ANSWER_HISTOGRAM` and renders a real-time completion bar based on:

- confirmed answers count
- total team count

The final results screen is driven from the `FINISHED` room state and `finalReport`, including:

- final team ranking
- total score per team
- per-question awarded points for each team

## Production Build

During local development, the frontend uses the Vite proxy configuration:

- `/api -> http://localhost:8080`
- `/ws -> ws://localhost:8080`

That allows the SPA and Spring Boot backend to run together without hardcoding environment-specific URLs.

Suggested local run flow:

1. Start PostgreSQL and Redis for the Spring Boot service.
2. Run the backend from `dip2` on port `8080`.
3. Run the frontend from `dip_quizzes` with Vite so proxied REST and WebSocket traffic reaches the backend.

For a production deployment, the same frontend can instead be served behind a reverse proxy that forwards `/api` and `/ws` to Spring Boot, or packaged alongside the backend with container orchestration.

## Verification Notes

The frontend TypeScript compilation succeeds with `node_modules/.bin/tsc -b`.

The full Vite production build is currently blocked in this workspace by the installed Node version:

- current environment: Node `18.19.1`
- required by the installed Vite toolchain: Node `20.19+`

To run `npm run build` successfully, the frontend environment needs to be upgraded to a supported Node 20+ runtime.

# Infrastructure Report

## Infrastructure Overview

The local infrastructure stack is orchestrated with `docker-compose.yml` and currently includes:

- `postgres` using `postgres:16-alpine` as the primary relational database for Spring Boot JPA data
- `redis` using `redis:7-alpine` as the room/session store and locking backend for real-time gameplay state

Both services persist data to bind-mounted local volumes under:

- `./docker/postgres/data`
- `./docker/redis/data`

Both services also define healthchecks so a future backend container can safely use `depends_on` with `condition: service_healthy` before starting.

## Startup Instructions

To start the local database services:

```bash
docker-compose up -d
```

To stop them:

```bash
docker-compose down
```

The backend configuration is wired to environment variables through `application.yml`, including:

- `spring.datasource.url: ${DB_URL:...}`
- `spring.datasource.username: ${DB_USERNAME:...}`
- `spring.datasource.password: ${DB_PASSWORD:...}`
- `spring.data.redis.host: ${REDIS_HOST:...}`
- `spring.data.redis.port: ${REDIS_PORT:...}`
- `app.jwt.secret: ${JWT_SECRET:...}`
- `app.jwt.access-token-expiration: ${JWT_EXPIRATION:PT2H}`
- `spring.security.oauth2.client.registration.google.client-id: ${GOOGLE_CLIENT_ID:...}`
- `spring.security.oauth2.client.registration.google.client-secret: ${GOOGLE_CLIENT_SECRET:...}`

## Security Note

The generated `.env` file contains sensitive values such as database credentials and the JWT signing secret. It should not be committed to source control.

`.env` has been added to `.gitignore` so the local secrets file stays untracked by default.
