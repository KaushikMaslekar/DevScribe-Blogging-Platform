# Spring Boot Layers and Architecture

This document describes the current backend structure for DevScribe and how the main Spring Boot layers fit together.

## 1. High-Level Architecture

DevScribe uses a classic layered Spring Boot backend:

- `controller` layer exposes HTTP endpoints.
- `service` layer contains business rules and transaction boundaries.
- `repository` layer handles database access with Spring Data JPA.
- `entity` layer maps the domain model to PostgreSQL tables.
- `dto` layer shapes request and response payloads.
- `security` layer handles JWT authentication and authorization.
- `exception` layer converts backend errors into consistent API responses.
- `util` layer contains reusable helpers such as slug generation.

The frontend calls the backend through `/api` endpoints, and the backend persists data in PostgreSQL through JPA and Flyway-managed schema migrations.

## 2. Request Flow

Typical request flow:

1. The client sends an HTTP request to a controller.
2. Spring Security authenticates the request when needed.
3. The controller validates the request body and forwards it to a service.
4. The service applies business rules and coordinates repositories.
5. Repositories read or write entities in PostgreSQL.
6. The service maps entities to DTOs and returns the response.
7. The exception layer converts known failures into structured JSON errors.

## 3. Layer Responsibilities

### Controller Layer

Controllers are thin HTTP adapters.

Current examples:

- `AuthController`
- `PostController`
- `TagController`
- `UserController`
- `CommentController` *(NEW - Phase 1)*

Responsibilities:

- Bind request parameters and bodies.
- Validate input with `jakarta.validation`.
- Return HTTP status codes and response DTOs.

### Service Layer

Services contain the real application logic.

Current examples:

- `AuthService`
- `PostService`
- `TagService`
- `UserService`
- `CommentService` *(NEW - Phase 1)*

Responsibilities:

- Enforce business rules.
- Apply transactional boundaries.
- Coordinate repositories and helper utilities.
- Handle authorization decisions that depend on the current user.

### Repository Layer

Repositories are Spring Data JPA interfaces.

Current examples:

- `UserRepository`
- `PostRepository`
- `TagRepository`
- `CommentRepository` *(NEW - Phase 1)*

Responsibilities:

- Fetch and persist entities.
- Expose query methods for filters and lookups.
- Keep database logic out of controllers and services.

### Entity Layer

Entities represent the persisted domain model.

Current examples:

- `User`
- `Post`
- `Tag`
- `PostStatus`
- `UserRole`
- `Comment` *(NEW - Phase 1)*
- `CommentStatus` *(NEW - Phase 1)*

Responsibilities:

- Map directly to PostgreSQL tables.
- Define relationships such as post-author and post-tags.
- Keep persistence concerns close to the data model.

### DTO Layer

DTOs define the API contract.

Current examples:

- `AuthRegisterRequest`
- `AuthLoginRequest`
- `AuthResponse`
- `AuthTokenPayload`
- `UserMeResponse`
- `CreatePostRequest`
- `UpdatePostRequest`
- `UpdatePostTagsRequest`
- `PostSummaryResponse`
- `PostDetailResponse`
- `CreateCommentRequest` *(NEW - Phase 1)*
- `CommentResponse` *(NEW - Phase 1)*
- `CommentThreadResponse` *(NEW - Phase 1)*
- `AuthorInfo` *(NEW - Phase 1)*

Responsibilities:

- Prevent leaking internal entities directly to clients.
- Keep request and response shapes stable.
- Make validation explicit at the API boundary.

### Security Layer

Security is stateless and JWT-based.

Current components:

- `SecurityConfig`
- `JwtAuthenticationFilter`
- `JwtTokenProvider`
- `JwtProperties`
- `CustomUserDetailsService`

Responsibilities:

- Authenticate requests from bearer tokens or HTTP-only cookies.
- Protect write operations.
- Expose the current authenticated user to the service layer.

### Exception Layer

The exception layer turns backend failures into readable JSON.

Current component:

- `GlobalExceptionHandler`

Responsibilities:

- Map validation errors and response status exceptions.
- Keep error payloads consistent for the frontend and Postman.

### Utility Layer

Current example:

- `SlugUtil`

Responsibilities:

- Convert titles and tag names into stable URL-safe slugs.

## 4. Data Model

Current persisted tables include:

- `users`
- `posts`
- `tags`
- `post_tags`
- `view_tracking`
- `comments` *(NEW - Phase 1)*

Important relationships:

- One user can author many posts.
- One post belongs to one author.
- One post can have many tags.
- One tag can belong to many posts.
- One post can have many comments (hierarchical tree).
- One user can author many comments.
- One comment can have many child replies (parent_comment_id relationship).

## 5. Backend Modules Implemented So Far

### Module 1

- Spring Boot foundation
- PostgreSQL and Flyway baseline
- Frontend scaffold and API client setup

### Module 2

- JWT authentication
- Register, login, and current-user endpoints
- Spring Security configuration

### Module 3

- Post CRUD
- Publish workflow
- Owner-based authorization

### Module 4

- Tag entity and tag repository
- Tag assignment on posts
- Tag filtering on post lists
- Tag display in frontend views

### Module 11: Post Likes & Reactions

- `PostLike` entity for user reactions
- Like/unlike endpoints
- Likes count display on posts

### Phase 1: Comments System *(COMPLETED - April 23, 2026)*

**Backend:**
- `Comment` entity with threaded parent/child relationships
- `CommentStatus` enum (ACTIVE, FLAGGED, DELETED)
- `CommentRepository` with hierarchical query methods
- `CommentService` with create/list/delete/flag operations
- `CommentController` exposing REST endpoints:
  - `GET /posts/{postId}/comments` - Get paginated comments with nested replies
  - `GET /posts/{postId}/comments/count` - Get comment count
  - `POST /posts/{postId}/comments` - Create comment or reply
  - `DELETE /posts/{postId}/comments/{commentId}` - Delete comment
  - `POST /posts/{postId}/comments/{commentId}/flag` - Flag for moderation
- Security rules: read-public, write-authenticated
- `V13__comments_schema.sql` Flyway migration
- Integration tests with 7+ test cases

**Frontend:**
- Comment types and API client (`comment-api.ts`)
- `CommentComposer` component - form with optimistic submission
- `CommentItem` component - display with author info, delete/flag actions
- `CommentSection` container - pagination, threading, reply state management
- Integration on post detail page (`/posts/[slug]`)
- Relative timestamps with date-fns
- Sign-in prompts for unauthenticated users

## 6. Architecture Notes

- Controllers should stay thin and avoid business logic.
- Services should be the main place for rules, validation decisions, and transaction boundaries.
- Repositories should remain focused on persistence only.
- DTOs should be used for all public request and response shapes.
- Security should be stateless to keep the API simple for the frontend and Postman.

## 7. Typical Feature Pattern

When adding a new feature, the usual order is:

1. Add or update entities.
2. Add repository queries.
3. Add service logic.
4. Add controller endpoints.
5. Add or update DTOs.
6. Add frontend API calls and UI.
7. Validate with compile/run checks and Postman.

## 8. Next Modules & Phases

**Phase 2 (Next Priority): Enhanced Reactions System**
- Expand from basic "like" to multi-type emoji reactions
- Reaction type enum (like, love, celebrate, insightful, etc.)
- Reaction toggle/upsert endpoints
- Aggregate reaction counts and per-user state

**Phase 3: Series Frontend Navigation**
- Series breadcrumbs on post detail pages
- Previous/next post navigation in series
- Series table of contents

**Future Modules:**
- Module 5: Rich editor and markdown pipeline
- Module 6: Autosave reliability layer
- Module 7: Realtime synchronization
- Module 8: Collaborative editing
- Module 9: Advanced search & discovery
- Module 10: Analytics & engagement
- Module 11+: Social features, moderation, performance optimization
