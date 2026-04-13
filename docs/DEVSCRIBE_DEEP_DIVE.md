# DevScribe Deep Dive

Last updated: 2026-04-14

## 1) Product and System Overview

DevScribe is a full-stack blogging platform with a Spring Boot API and a Next.js frontend.

Current implemented capability set includes:

- Auth (register, login, profile)
- Post lifecycle (draft, update, publish, schedule)
- Tags and search (SQL LIKE + trigram indexes)
- Autosave snapshots and restore
- Likes, bookmarks, follows
- Realtime broadcast and collaboration session support
- Security hardening filters (headers, correlation ID, request logging, rate limiting)

## 2) Monorepo Structure

- `src/main/java/com/devscribe/*`: Backend Java code
- `src/main/resources/*`: Backend config and Flyway migrations
- `src/test/*`: Backend tests
- `frontend/*`: Next.js app (App Router)
- `.github/workflows/ci.yml`: CI checks (backend + frontend)
- `docker-compose.yml`: Local PostgreSQL + Redis
- `docs/modules/*`: Module-by-module progression docs

## 3) Backend Architecture

### 3.1 Layering

Backend follows a standard layered architecture:

- Controller layer: HTTP endpoints and DTO mapping
- Service layer: domain logic, authorization enforcement, orchestration
- Repository layer: data access with Spring Data JPA + native SQL where needed
- Entity layer: JPA entities mapped to PostgreSQL tables
- Security layer: JWT auth, filter chain, CORS, rate limiting, headers, request tracing
- Utility/realtime layer: slug generation, correlation ID, realtime event publishing

### 3.2 Core Packages

- `controller`: `AuthController`, `PostController`, `TagController`, `UserController`, `PostCollaborationController`, `HealthCheckController`
- `service`: `AuthService`, `PostService`, `UserService`, `TagService`, `PostCollaborationService`
- `repository`: post, user, tag, likes, bookmarks, follows, collaborators, snapshots repositories
- `security`: `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`, `RateLimitingFilter`, `SecurityHeadersFilter`, `CorrelationIdFilter`, `RequestLoggingFilter`
- `entity`: `User`, `Post`, `Tag`, `PostLike`, `PostBookmark`, `PostAutosaveSnapshot`, `UserFollow`, etc.

## 4) Request Lifecycle (Backend)

Typical protected request flow:

1. `RequestLoggingFilter` logs inbound request metadata.
2. `SecurityHeadersFilter` applies response security headers.
3. `CorrelationIdFilter` attaches/propagates `X-Correlation-ID`.
4. `RateLimitingFilter` enforces endpoint limits.
5. `JwtAuthenticationFilter` resolves user from Bearer token or auth cookie.
6. Spring Security authorization checks route rules.
7. Controller delegates to service.
8. Service applies business and ownership checks.
9. Repository persists/fetches data.
10. DTO response returned with correlation header.

## 5) Security Model

### 5.1 Authentication

- JWT token generation and validation via `JwtTokenProvider`
- Token accepted from:
  - `Authorization: Bearer <token>`
  - Cookie `devscribe_access_token`
- Stateless session policy (`SessionCreationPolicy.STATELESS`)

### 5.2 Authorization

Current route-level rules in `SecurityConfig` include:

- Public: basic post listing/detail, auth register/login, health
- Authenticated: `/auth/me`, post mutations, feed/bookmarks, follows, profile updates

Role field exists on users and authorities are emitted as `ROLE_<role>`.
Current enum values are `USER`, `ADMIN`.

### 5.3 Security Hardening

Implemented hardening:

- Security headers (HSTS, frame deny, content-type protection, CSP-related controls)
- Rate limiting filter (auth endpoints + post creation limits)
- Correlation ID tracing for request diagnostics
- Structured request/response logging via dedicated filter
- CORS allowlist via env override (`CORS_ALLOWED_ORIGINS`)

## 6) API Surface (Current)

### 6.1 Auth

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`

### 6.2 Posts

- `GET /api/posts` (supports page, size, mine, following, status, tag, query)
- `GET /api/posts/feed`
- `GET /api/posts/bookmarks`
- `GET /api/posts/{slug}`
- `POST /api/posts`
- `POST /api/posts/autosave`
- `GET /api/posts/{id}/autosave-snapshots`
- `POST /api/posts/{id}/autosave-snapshots/{snapshotId}/restore`
- `PUT /api/posts/{id}`
- `DELETE /api/posts/{id}`
- `POST /api/posts/{id}/publish`
- `PUT /api/posts/{id}/tags`
- `POST /api/posts/{id}/like`
- `DELETE /api/posts/{id}/like`
- `POST /api/posts/{id}/bookmark`
- `DELETE /api/posts/{id}/bookmark`

### 6.3 Collaboration

- `GET /api/posts/{postId}/collaboration/session`
- `GET /api/posts/{postId}/collaboration/collaborators`
- `POST /api/posts/{postId}/collaboration/collaborators`
- `DELETE /api/posts/{postId}/collaboration/collaborators/{userId}`

### 6.4 Users and Tags

- `GET /api/tags`
- `GET /api/users/{username}`
- `POST /api/users/{username}/follow`
- `DELETE /api/users/{username}/follow`
- `PUT /api/users/me/profile`
- `DELETE /api/users/{id}`

### 6.5 Health/Observability

- `/api/health/live`
- `/api/health/ready`
- `/api/health/detailed`
- `/api/actuator/{health,info,metrics,prometheus}`

## 7) Backend Domain Model

### 7.1 User

Fields:

- identity: `id`, `email`, `username`
- profile: `displayName`, `bio`, `avatarUrl`
- security: `passwordHash`, `role`
- timestamps: `createdAt`, `updatedAt`

### 7.2 Post

Fields:

- ownership: `author`
- content: `slug`, `title`, `markdownContent`, `excerpt`
- lifecycle: `status`, `publishedAt`, `scheduledPublishAt`
- concurrency and autosave: `version`, `autosaveRevision`
- metadata: tags relation + timestamps

`@Version` on `version` enables optimistic locking.

### 7.3 Social and Collaboration

- Likes table (`post_likes`)
- Bookmarks table (`post_bookmarks`)
- Follows table (`user_follows`)
- Collaborators table (`post_collaborators`)
- Autosave snapshots table (`post_autosave_snapshots`)

## 8) Search and Performance Strategy

Current search behavior:

- SQL query with optional `query` and `tag` filters
- Case-insensitive match across `title`, `excerpt`, `markdown_content`
- Only `PUBLISHED` posts are searchable
- Sort: `published_at` desc, then `updated_at` desc

Performance strategy in DB:

- Trigram GIN indexes (`pg_trgm`) for text search fields
- Composite indexes for listing/filtering hot paths
- Additional summary cover index to reduce table lookups

## 9) Database and Migration Evolution

Flyway migrations currently present:

- `V1__initial_schema.sql`: users, posts, tags, post_tags, view_tracking
- `V2__post_autosave_revision.sql`: adds `autosave_revision`
- `V3__post_collaboration.sql`: adds post collaborators
- `V4__post_search_indexes.sql`: trigram search indexes
- `V5__performance_indexes.sql`: listing/filter index optimization
- `V6__post_likes.sql`: likes relation
- `V7__social_profile_features.sql`: user profile fields, follows, bookmarks
- `V8__autosave_snapshots.sql`: autosave history table
- `V9__scheduled_publishing.sql`: scheduled publishing columns/indexes

Current deployment mode uses:

- `spring.jpa.hibernate.ddl-auto=validate`
- Flyway as source of schema truth

## 10) Frontend Architecture

### 10.1 Stack

- Next.js 16 App Router
- React 19 + TypeScript strict
- Tailwind CSS v4 + UI primitives
- TanStack Query for data synchronization
- Axios client with interceptors
- TipTap editor and markdown conversion pipeline
- Supabase realtime + Hocuspocus provider for collaboration paths

### 10.2 Route Map

Current top-level pages:

- `/` (public home)
- `/feed`
- `/bookmarks`
- `/dashboard`
- `/posts/[slug]`
- `/u/[username]`
- `/login`
- `/register`

### 10.3 Client API Layer

`frontend/src/lib` contains focused API modules:

- `auth-api.ts`, `user-api.ts`
- `post-api.ts`, `tag-api.ts`
- `collaboration-api.ts`, `collaboration-session.ts`
- `post-realtime.ts`, `autosave.ts`, `markdown.ts`

`api-client.ts` behavior:

- Base URL from `NEXT_PUBLIC_API_URL` fallback `http://localhost:8080/api`
- Sends credentials/cookies
- Injects Bearer token from local storage
- On 401, clears token and redirects to `/login`

### 10.4 Route Protection

`middleware.ts` enforces auth on selected routes (currently dashboard/editor prefixes) by checking the auth cookie.

## 11) Infrastructure and Runtime

Local dependencies (`docker-compose.yml`):

- PostgreSQL 17 (db: `devscribe`)
- Redis 7

Maven dependencies indicate:

- Spring Web, JPA, Security, Validation, Actuator
- Flyway + PostgreSQL
- JWT (jjwt)
- Redis starter (optional)
- Caffeine caching

## 12) CI Workflow

`.github/workflows/ci.yml` has two jobs:

Backend job:

1. Checkout
2. Setup Java 21
3. `./mvnw -B -DskipTests flyway:validate`
4. `./mvnw -B test`
5. `./mvnw -B -DskipTests compile`

Frontend job:

1. Checkout
2. Setup Node 22
3. `npm ci` (in `frontend`)
4. `npm run lint`
5. `npm run build`

Important operational note:

- On Linux runners, `mvnw` must be executable in git mode (`100755`).

## 13) Configuration and Environment Variables

Backend important env vars/properties:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`, `JWT_EXPIRATION_MS`
- `CORS_ALLOWED_ORIGINS`
- `REALTIME_ENABLED`, `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `REALTIME_CHANNEL`

Frontend important env vars:

- `NEXT_PUBLIC_API_URL`
- `NEXT_PUBLIC_SUPABASE_URL`
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`
- `NEXT_PUBLIC_REALTIME_POST_CHANNEL`
- `NEXT_PUBLIC_HOCUSPOCUS_URL`

## 14) Testing Status

Current baseline:

- Backend unit/integration tests are present and run in CI
- Frontend CI runs lint and build

Gap for release confidence:

- Missing minimum E2E coverage for key user flows (auth, editor, publish)

## 15) Known Strengths and Current Risks

### Strengths

- Clear backend layering and DTO boundaries
- Flyway-based schema versioning
- Good initial hardening (headers, rate limiting, correlation IDs)
- Realtime and collaboration foundation already integrated
- Search index strategy already started

### Risks Before First Public Release

- Incomplete role workflow granularity (`USER/ADMIN` only)
- No soft delete/restore workflow for safer content ops
- No centralized critical-action audit log table
- Feature-flag rollout framework missing
- E2E safety net not yet in place

## 16) Priority Implementation Track (Urgent)

1. Keep CI green consistently (including `mvnw` executable bit).
2. Cleanly commit current work tree changes.
3. Implement V1 safety modules:
   - role-based writer/editor/admin workflow
   - soft delete + restore bin
   - audit logs for critical actions
   - feature flags by cohort
4. Add minimum Playwright E2E coverage for auth/editor/publish.
5. Expand baseline rate limiting by endpoint and user tier.

## 17) Practical Local Runbook

Backend:

1. `docker compose up -d`
2. `./mvnw.cmd spring-boot:run`

Frontend:

1. `cd frontend`
2. `npm install`
3. `npm run dev`

URLs:

- Frontend: `http://localhost:3000`
- Backend base: `http://localhost:8080/api`

---

If this document is used as onboarding, read sections in this order:

1. 1-3 (system + architecture), 2) 6-9 (API + data), 3) 10-13 (frontend + runtime), 4) 15-16 (risk + release priorities).
