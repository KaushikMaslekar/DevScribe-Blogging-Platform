# DevScribe

DevScribe is a production-grade, developer-focused blogging platform.

## Monorepo Layout

- `frontend/`: Next.js 16, React 19, TypeScript strict, Tailwind, shadcn/ui primitives
- `src/`: Spring Boot backend source (root module: `devscribe-api`)
- `docker-compose.yml`: Local PostgreSQL and Redis services

## Module Delivery Strategy

This repository is implemented module-by-module. Each module is committed and pushed independently.

Detailed module docs are available at:

- `docs/modules/MODULES_INDEX.md`

### Module 1 (Current)

- Backend foundation with Spring Boot + PostgreSQL + Flyway baseline
- Initial SQL migration for core entities
- Frontend foundation with App Router and required core dependencies
- Axios client with auth/error interceptors
- TanStack Query provider setup
- Middleware-based protected route baseline

### Module 2 (Current)

- JWT authentication implemented end-to-end
- Backend auth endpoints: register, login, me
- Stateless Spring Security filter chain with JWT validation
- BCrypt password hashing and user persistence
- Frontend login/register pages connected to backend APIs
- Token lifecycle handling with secure-cookie preference and localStorage fallback

### Module 3 (Current)

- Backend post domain and status model implemented
- Endpoints implemented: list, detail by slug, create, update, delete, publish
- Owner-based authorization rules for post mutation actions
- Public homepage published-feed endpoint usage
- Dashboard post management UI with create, filter, publish, delete flows
- Post detail page route wired to backend slug endpoint

### Module 4 (Current)

- Tag entity and tag-post relationship implemented
- Post tagging APIs and tag filtering on post lists
- Tag chips displayed in feed, dashboard, and detail views
- Public tag browsing endpoint available

### Module 5 (Current)

- TipTap-based rich writing editor added to the dashboard workflow
- Markdown conversion pipeline added for save/load compatibility
- Live preview rendering for markdown content
- Post detail page now renders markdown as formatted HTML

### Upcoming Modules

- Module 6: Fault-tolerant autosave system
- Module 7: Realtime updates (Supabase)
- Module 8: Collaborative editing (TipTap + Y.js + Hocuspocus)
- Module 9: Search (SQL first, Elasticsearch optional)
- Module 10+: hardening, observability, performance, and production readiness

## Run Locally

### Backend

1. Start local services:
   - `docker compose up -d`
2. Run the API:
   - `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`)

Backend base URL: `http://localhost:8080/api`

### Frontend

1. Go to frontend app:
   - `cd frontend`
2. Install dependencies:
   - `npm install`
3. Run dev server:
   - `npm run dev`

Frontend URL: `http://localhost:3000`
