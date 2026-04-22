# DevScribe - Remaining Features & Roadmap

**Last Updated:** April 21, 2026  
**Current Status:** Post Series (Modules 1-11) completed. Ready for next phase.

---

## ✅ Completed Features

### Core Modules (1-10)
- [x] Module 01: Foundation and Monorepo Setup
- [x] Module 02: JWT Authentication and Security
- [x] Module 03: Posts CRUD and Publish Workflow
- [x] Module 04: Tagging and Taxonomy
- [x] Module 05: Rich Editor and Markdown Pipeline
- [x] Module 06: Autosave Reliability Layer
- [x] Module 07: Realtime Synchronization
- [x] Module 08: Collaborative Editing
- [x] Module 09: Search and Discovery
- [x] Module 10: Hardening and Production Readiness

### Additional Features Implemented
- [x] **Post Likes & Reactions** (Module 11 - Basic likes)
- [x] **Post Series Management**
  - Create/list series
  - Attach posts to series with ordering
  - Reorder posts within series
  - Detach/move posts between series
  - Public series detail navigation
  - Post counts in series summaries
- [x] **Scheduled Publishing** - Schedule posts for future publication
- [x] **Autosave Timeline** - View revision history and restore older snapshots
- [x] **Revision Compare** - Side-by-side comparison of post revisions
- [x] **Reading Time & Table of Contents** - Auto-calculated reading time and hardened TOC extraction
- [x] **Dashboard Admin Flows** - Restore from trash, admin flags
- [x] **Markdown Rendering** - Full hardening with TOC and reading time on post pages

---

## 📋 Remaining Features (Priority Order)

### Phase 1: Comments System (Backend + Frontend)
- [ ] **Backend Schema & API**
  - [ ] Comment entity with thread structure (parent/child relationships)
  - [ ] Comment creation endpoint (nested replies support)
  - [ ] Comment list endpoint (threaded hierarchical retrieval)
  - [ ] Comment deletion endpoint (ownership enforcement)
  - [ ] Comment moderation support (mark as spam/inappropriate)
  - [ ] Database migrations (Flyway)
  
- [ ] **Frontend UI**
  - [ ] Comment list component (hierarchical rendering)
  - [ ] Comment composer (reply form)
  - [ ] Reply expansion/collapsing
  - [ ] Optimistic comment creation
  - [ ] Comment deletion with confirmation
  - [ ] Display on post detail page

### Phase 2: Enhanced Reactions System
- [ ] **Backend Schema & API**
  - [ ] Reaction type enum (like, love, celebrate, insightful, etc.)
  - [ ] Reaction entity with user + post + type
  - [ ] Reaction toggle/upsert endpoint
  - [ ] Aggregate reaction counts per post
  - [ ] Per-user current reaction state retrieval
  
- [ ] **Frontend UI**
  - [ ] Reaction picker/button group
  - [ ] Show total counts per reaction type
  - [ ] Highlight user's current reaction
  - [ ] Optimistic updates on click
  - [ ] Display on feed, detail, and dashboard
  - [ ] Animate reaction changes

### Phase 3: Series Frontend Navigation
- [ ] Series navigation breadcrumb on post detail pages
- [ ] "Previous/Next in Series" buttons
- [ ] Series table of contents sidebar (if series has many posts)
- [ ] Series meta display (e.g., "Part 3 of 8")

### Phase 4: Advanced Collaboration Features
- [ ] **Comment @mentions** and notification subscriptions
- [ ] **Real-time comment notifications** (WebSocket integration)
- [ ] Comment editing and edit history
- [ ] Comment reactions/votes

### Phase 5: Analytics & Engagement
- [ ] Post view counter
- [ ] Engagement metrics dashboard (views, likes, comments, shares)
- [ ] Author analytics page showing post performance
- [ ] Time-series data for trending/popular posts

### Phase 6: Social & Sharing
- [ ] Social share buttons (copy link, Twitter, LinkedIn, etc.)
- [ ] Public post sharing with metadata (OG tags)
- [ ] Newsletter/email subscription for authors
- [ ] Follow author notifications

### Phase 7: Content Organization
- [ ] **Collections/Playlists** (similar to series but user-curated from any posts)
- [ ] Custom reading lists
- [ ] User-created topic pages

### Phase 8: Advanced Search & Discovery
- [ ] Full-text search on comments
- [ ] Advanced filters (date range, post type, series, author)
- [ ] Search analytics and trending searches
- [ ] Search refinement suggestions

### Phase 9: Moderation & Admin Tools
- [ ] Post reporting system
- [ ] Comment moderation dashboard
- [ ] Spam detection and filtering
- [ ] User reputation/trust scores

### Phase 10: Performance & Scale
- [ ] Database query optimization and indexing
- [ ] Caching strategy for frequently accessed data
- [ ] CDN integration for static assets
- [ ] API rate limiting refinement

---

## 🎯 Suggested Next Steps

**Recommended immediate next work:** Comments System (Phase 1)

**Why:**
- Comments are foundational for engagement
- Unblocks threaded discussions, which is critical for developer content
- Provides natural pathway to reactions enhancement
- UI patterns are well-established and clear

**Implementation order for Comments:**
1. Backend: Comment schema + create/list API
2. Backend: Comment moderation validation
3. Frontend: Comment list and reply composer
4. Frontend: Integrate on post detail pages

---

## 📊 Current Entity Model

### Existing Entities
- `User` - Authentication and profile
- `Post` - Blog content with status (DRAFT, PUBLISHED, SCHEDULED, ARCHIVED)
- `PostAutosaveSnapshot` - Revision history
- `PostBookmark` - User bookmarks
- `PostLike` - Like reactions
- `PostTag` - Post-tag associations
- `PostCollaborator` - Collaboration access
- `Series` - Post groupings
- `SeriesPost` - Ordered post membership in series
- `Tag` - Taxonomy
- `UserFollow` - Social graph
- `FeatureFlag` - Feature toggles
- `AuditLog` - System audit trail

### Pending Entities (for remaining features)
- `Comment` - Threaded comments (Phase 1)
- `Reaction` - Multi-type reactions (Phase 2)
- `CommentModeration` - Spam/abuse flagging (Phase 1)
- `PostView` - Analytics (Phase 5)
- `EngagementMetric` - Aggregated stats (Phase 5)

---

## 📝 Implementation Notes

- All new features should maintain the existing **monochrome professional UI** theme
- Use **Fira Code typography** for code blocks
- Keep API responses typed and consistent with existing DTO patterns
- Add **Flyway migrations** for all schema changes
- Include **security integration tests** for all authenticated endpoints
- Implement **optimistic updates** on the frontend for better UX
- Consider **Redis caching** for aggregate counts and popular content

---

## 🔗 Related Documentation

- Module docs: `docs/modules/`
- Implementation reports: `docs/implementation-reports/`
- Deep dive architecture: `docs/DEVSCRIBE_DEEP_DIVE.md`
- Spring Boot layers guide: `SPRING_BOOT_LAYERS_AND_ARCHITECTURE.md`

