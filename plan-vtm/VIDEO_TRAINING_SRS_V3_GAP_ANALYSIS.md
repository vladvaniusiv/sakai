# Sakai Video Training - Gap Analysis vs SRS v3.0

## Scope and source used

This analysis is based on the SRS PDF `Sakai_VTM_Requirements_SRS_v3.0.pdf` (March 2026), not on the planning prompt markdown.

Goal of this document:
- Explain what the SRS asks for.
- Compare that against what is currently implemented in the repository.
- Identify concrete gaps and implementation priorities.

## Executive summary

Current implementation provides a solid site-level MVP:
- Basic video CRUD in tool UI.
- External URL embedding and native upload into Sakai Resources.
- Captions/transcript basic CRUD.
- Basic analytics (view counts, unique viewers).
- Search and pagination/cursor in list and webapi endpoints.

However, the SRS v3.0 defines a broader product scope.
Major gaps remain in:
- YouTube/Vimeo private-channel integration via official APIs.
- Full visibility model semantics across contexts (Global/Course/Lesson and lesson-origin restrictions).
- Lessons bidirectional integration.
- External LMS API contract (`/api/v1`, OAuth2 clients, read+manage endpoints).
- Taxonomy/playlists/favorites and richer metadata model.
- Operational governance (quotas, audit log, webhooks, API client administration).

Overall status vs SRS:
- Must-have (P1): partial compliance.
- Should-have (P2): mostly pending.
- Could-have (P3): mostly pending.
- Future Phase (P4): intentionally not required now.

## What SRS v3.0 explicitly requires

### Product and architecture requirements

The SRS requires:
- Native Sakai tool with three access contexts: institution/global, course, lessons embedding.
- Primary storage on institutional media server.
- Optional secondary storage on YouTube/Vimeo private channels.
- External LMS REST API (read + manage existing videos, no create/delete), up to 20 clients.
- OAuth2-based security model for API clients and external platform integrations.

### Upload and ingestion requirements (core)

SRS requires:
- Direct upload formats at least: MP4, WebM, MOV, AVI, MKV.
- Server-side transcoding to H.264 MP4 + AAC, with quality tiers.
- Configurable max file size (default 2 GB).
- Chunked/resumable uploads for large files.
- Progress indicator during upload and transcoding.
- External link embedding with oEmbed providers (YouTube, Vimeo) plus generic iframe.
- Provider auto-detection and metadata extraction (thumbnail/title/duration where available).
- Link validation at save and periodic re-validation via cron.
- Broken-link flagging and instructor notifications.

### Visibility and access model requirements

SRS requires:
- Visibility scope assignment at upload: Global, Course, Lesson-only.
- Scope modifications after upload, with specific behavior guarantees.
- Restriction flag for lesson-originated content and API exclusion semantics.
- Withdraw (soft unpublish) semantics with catalog/search/API impact.
- Scheduled publish/expiry (P2).

### Lessons requirements

SRS requires:
- Embed VTM videos into Lessons via library picker.
- Promote lesson-uploaded videos into VTM library.
- Track lesson playback completion (P2).
- Show reference indicators for lesson usage (P2).

### Metadata and organization requirements

SRS requires richer metadata than current model, including:
- Duration, thumbnail URL, language, visibility scope, status, storage location, external platform IDs, allocations, categories, restriction flag.
- Hierarchical taxonomy and course grouping.
- Playlists (P2), favorites/watch-later (P2).

### Player, captions, analytics requirements

SRS requires:
- Advanced player controls and adaptive streaming behavior.
- Captions via SRT/VTT upload and optional auto-captioning bridge.
- Transcript panel synchronized with click-to-seek (P2).
- Chapter markers (P2).
- Rich analytics dashboard and export/API exposure.

### Private channel YouTube/Vimeo requirements

SRS requires:
- Admin configuration of YouTube/Vimeo platform accounts and OAuth credentials.
- Upload to configured private channels via official APIs.
- Playlist mapping/synchronization (P2).
- Privacy synchronization with VTM visibility status.
- Import existing platform videos (P2).
- Health monitoring for platform availability (P2).
- Playback proxy/controlled embed behavior.

### External LMS API requirements

SRS requires contract under `/api/v1`:
- OAuth2 client credentials, scope model, client lifecycle.
- OpenAPI spec + health endpoint.
- Read endpoints for catalog, video detail, course videos, categories/playlists (some P2).
- Management endpoints for existing video metadata/visibility/status/schedule.
- Explicitly no video create/delete via API.
- Webhooks (P2), usage metrics (P2), rate limits and governance.

## Current implementation snapshot

### Implemented now

- Tool MVC routes for list/new/edit/create/update/delete/details/analytics/captions.
- Site-scoped data model with basic metadata and date visibility window.
- Basic provider model: `NATIVE` and `EXTERNAL`.
- Native upload to Sakai content resource with server-side validation hardening.
- External URL embedding in details view (`iframe`).
- Explicit lifecycle model with `publicationStatus` (`DRAFT/PUBLISHED/WITHDRAWN/ARCHIVED`) and `visibilityScope` (`GLOBAL/COURSE/LESSON`) persisted on videos.
- Restriction flag `lessonOriginRestricted` persisted on videos and enforced in end-user visibility filters.
- Tool UI controls for visibility scope/publication status in create/edit and quick publish/withdraw actions in list/details.
- Visible catalog filtering excludes non-published videos and lesson-only scoped videos for end users.
- YouTube-only thumbnail extraction helper for list cards.
- Search and pagination/cursor in tool and webapi list endpoints.
- Webapi management endpoint for updating existing video metadata/visibility/publication/schedule (site-session model).
- `/api/v1` aliases now available for catalog/detail/manage endpoints and health check endpoint.
- `/api/v1/videos` and `/api/v1/videos/{videoId}` endpoints now available as contract-style aliases (site-scoped via `siteId` query parameter).
- `/api/v1/courses/{id}/videos` endpoint now available as course-scoped list alias.
- Lessons bridge endpoints now available to register lesson-video links and promote lesson resources into VTM (`/api/v1/lessons/...`).
- Hierarchical taxonomy model now available with category CRUD and video-category assignment support.
- Course-grouped catalog endpoint now available (`/api/v1/videos/grouped-by-course`).
- Storage quota enforcement now relies on Sakai Resources quota (`ContentHostingService`/`OverQuotaException`) and exposes usage/quota read values from site Resources collection.
- Audit for key lifecycle/taxonomy/lesson actions now posts into Sakai EventTrackingService (`video.training.*` events), aligned with platform event logging.
- Service-layer permission checks for manage/view/analytics/captions-manage.
- Basic analytics event recording and aggregate summary.
- Unit tests for service and webapi plus baseline e2e for key flows.

### Notable limitations in current code

- Restriction flag exists and affects end-user catalog/API visibility, but lessons-origin automatic setting and full cross-context semantics are not yet implemented.
- Lessons integration currently uses API bridge endpoints (link registration and promote-resource); direct Lessons picker UX wiring in lessonbuilder remains pending.
- No YouTube/Vimeo platform connector service, OAuth setup, or API-based uploads.
- No transcoding pipeline orchestration in VTM.
- No chunked/resumable upload handling in VTM flow.
- No link validation scheduler for external URLs.
- No lessons picker/promote integrations.
- API surface is Sakai-session site endpoints, not SRS `/api/v1` OAuth client API.
- Management API is now partially present but still under Sakai-session webapi path (not `/api/v1` OAuth client contract).
- `/api/v1` routes exist but still rely on Sakai session auth (OAuth2 client-credentials model pending).

## Requirement-by-requirement status (SRS)

Legend:
- `DONE`: implemented as specified.
- `PARTIAL`: implemented in reduced form.
- `MISSING`: not implemented.
- `DEFERRED`: explicitly future phase in SRS.

### P1 (Must-have)

- FR-010 Direct File Upload: `PARTIAL`
  - Has native upload and file validation; lacks resumable/chunked, transcoding workflow, configurable 2 GB default.
- FR-012 External Link Embedding: `PARTIAL`
  - Has external URL embedding; missing provider metadata extraction completeness, periodic validation, broken-link management.
- FR-020 Visibility Scope Assignment (Global/Course/Lesson): `PARTIAL`
  - Scope field and UI assignment/edit exist; full cross-context semantics (global catalog, lesson-only behavior in Lessons context) remain incomplete.
- FR-021 Post-Upload Visibility Modification semantics: `PARTIAL`
  - Scope/status can be modified after upload via edit and publish/withdraw actions; some SRS guarantees across Lessons/API contexts remain pending.
- FR-022 Lesson-origin restriction behavior: `PARTIAL`
  - Restriction flag exists and excludes videos from non-manager visibility/catalog/API list behavior; automatic lessons-origin tagging and full SRS semantics remain pending.
- FR-023 Withdrawal soft unpublish behavior: `PARTIAL`
  - Explicit withdrawn lifecycle state now exists and is enforced in end-user catalog visibility, but full API/lessons synchronization semantics remain pending.
- FR-030 Embed VTM in Lessons: `PARTIAL`
  - API bridge endpoints now allow Lessons to register/manage lesson-video links, but direct lessonbuilder picker UI integration remains pending.
- FR-031 Promote Lesson video to VTM: `PARTIAL`
  - Promote-resource API is implemented for lesson resources into VTM library with lesson-restricted defaults; end-to-end lessonbuilder UI flow remains pending.
- FR-040 Core metadata complete set: `PARTIAL`
  - Missing several required fields.
- FR-050 Hierarchical taxonomy: `PARTIAL`
  - Category hierarchy model and API CRUD/assignment are implemented; tool UI and advanced taxonomy UX are pending.
- FR-051 Course grouping on global page: `PARTIAL`
  - API endpoint for grouped-by-course catalog is implemented; full global page UX in tool remains pending.
- FR-060 Full-text search including captions/categories: `PARTIAL`
  - Search exists for title/description/source only.
- FR-070 Embedded player features set: `PARTIAL`
- FR-071 Captions/subtitles full behavior: `PARTIAL`
- FR-075 Third-party auto-captioning integration: `MISSING`
- FR-080 View tracking full telemetry scope: `PARTIAL`
- FR-100 Storage quota management: `PARTIAL`
  - Uses Sakai Resources quota model (shared with site Resources) for enforcement and reporting values; dedicated VTM quota administration UX/API is intentionally not implemented.
- FR-102 Audit log: `PARTIAL`
  - VTM actions are logged through Sakai EventTrackingService (platform event system); dedicated VTM audit persistence/query API is not implemented.
- FR-110 External platform account config (YouTube/Vimeo): `MISSING`
- FR-111 Upload to private channel: `MISSING`
- FR-113 Privacy status synchronization: `MISSING`
- FR-116 Playback proxy for private channels: `MISSING`
- FR-120 RESTful `/api/v1` OpenAPI read+manage API: `PARTIAL`
  - Webapi endpoints exist but not contract/SRS model.
- FR-121 OAuth2 client auth/scopes/max 20: `MISSING`
- FR-122 Catalog endpoint contract: `PARTIAL`
  - `/api/v1/videos` now available and permission-filtered, but still tied to Sakai-session auth and siteId query semantics instead of OAuth2/OpenAPI contract final shape.
- FR-123 Single video detail contract: `PARTIAL`
  - `/api/v1/videos/{videoId}` now available and permission-filtered, but still tied to Sakai-session auth and siteId query semantics instead of OAuth2/OpenAPI contract final shape.
- FR-124 Course videos endpoint `/api/v1/courses/{id}/videos`: `PARTIAL`
  - Endpoint exists and returns permission-filtered course catalog list, but still uses Sakai-session auth and not full OAuth2/OpenAPI contract semantics.
- FR-125 Embed URL generation signed endpoint: `MISSING`
- FR-126 Management endpoints for existing videos via API: `PARTIAL`
  - Endpoint exists for metadata/visibility/status/schedule updates on existing videos, including `/api/v1` alias; OAuth2 contract and full SRS scope remain pending.
- FR-131 API health endpoint: `PARTIAL`
  - `/api/v1/video-training/health` exists and reports service status, but full SRS external API security/governance model is not yet implemented.
- FR-201 Backward-compatible Lessons embeds: `MISSING` (not validated/implemented in lessons path)

### P2 (Should-have)

- FR-013 LTI source integration: `MISSING`
- FR-024 Scheduled publishing/expiry: `PARTIAL` (date fields exist; no full lifecycle state handling)
- FR-032 Lesson completion tracking: `MISSING`
- FR-033 Reference indicator in N lessons: `MISSING`
- FR-052 Playlists: `MISSING`
- FR-053 Favourites/watch later: `MISSING`
- FR-061 Faceted filtering: `MISSING`
- FR-062 Sort options set: `MISSING`
- FR-072 Transcript synchronized panel: `MISSING`
- FR-073 Chapter markers: `MISSING`
- FR-081 Instructor analytics dashboard full: `PARTIAL`
- FR-082 Student progress view: `MISSING`
- FR-090 Student notifications: `MISSING`
- FR-091 Instructor notifications: `MISSING`
- FR-101 Bulk operations admin: `MISSING`
- FR-112 Private-channel playlist management: `MISSING`
- FR-114 External platform import: `MISSING`
- FR-115 Platform health monitoring: `MISSING`
- FR-127 Categories endpoint: `MISSING`
- FR-128 Playlists endpoints: `MISSING`
- FR-129 Webhooks: `MISSING`
- FR-130 API usage reporting: `MISSING`

### P3 (Could-have)

- FR-025 Conditional release: `MISSING`
- FR-041 Custom metadata: `MISSING`
- FR-074 Instructor annotations: `MISSING`
- FR-083 Admin analytics institutional: `MISSING`
- FR-103 Moderation workflow: `MISSING`
- FR-104 Retention/archive policy: `MISSING`
- FR-200 Migration utility (optional): `MISSING`

### P4 (Future phase)

- FR-011 Bulk upload: `DEFERRED`
- FR-095 Video comments: `DEFERRED`

## Non-functional requirement status (high-level)

- NFR-010 playback latency: `NOT VERIFIED`
- NFR-011 upload throughput concurrency: `NOT VERIFIED`
- NFR-012 search/api latency SLAs: `PARTIAL` (performance work done; SLA evidence absent)
- NFR-020 scaling targets including API gateway sizing: `PARTIAL`
- NFR-030 WCAG 2.1 AA: `PARTIAL`/`NOT VERIFIED`
- NFR-040 signed URL access model: `MISSING`
- NFR-041 content protection at rest/transit details: `PARTIAL`
- NFR-042 API security hardening stack: `MISSING`
- NFR-043 external credential security model: `MISSING`
- NFR-050 uptime/retry policy: `MISSING`
- NFR-060 browser/mobile support: `NOT VERIFIED`
- NFR-070 i18n/RTL + API code/message format: `PARTIAL`
- NFR-080 GDPR/FERPA specific controls: `MISSING`

## Critical gap clusters

### 1) External platform integration (YouTube/Vimeo)

What SRS asks:
- Official API-based private channel uploads, OAuth credential management, privacy sync, import, health checks.

Current state:
- External URL embed exists; no connector services or platform account lifecycle.
- YouTube thumbnail helper only; no Vimeo equivalent provider adapter pipeline.

Impact:
- Central SRS capability is not delivered.

### 2) Visibility model and content lifecycle

What SRS asks:
- Explicit scope model (Global/Course/Lesson), restriction semantics, withdrawal lifecycle and synchronization behavior.

Current state:
- Site-level model with release/retract windows and permission checks.
- Explicit `visibilityScope` and `publicationStatus` fields are now implemented, editable in tool UI, and enforced in end-user visible queries (`PUBLISHED` and not `LESSON`).
- `lessonOriginRestricted` flag is now implemented and enforced in end-user visibility/catalog/API filtering.
- Remaining gap is full multi-context behavior (global/lessons), restriction flag semantics, and broader synchronization rules.

Impact:
- Core publishing/governance behaviors are missing.

### 3) External LMS API contract

What SRS asks:
- `/api/v1` REST + OpenAPI + OAuth2 + read/manage endpoints for existing videos only.

Current state:
- Site-based webapi GET endpoints under existing Sakai session model.
- No OAuth clients, no manage endpoints, no OpenAPI/health endpoints per contract.

Impact:
- Integration commitment for external LMSs is largely unmet.

### 4) Lessons and learning flow integration

What SRS asks:
- Bidirectional Lessons integration, completion tracking, reference indicators.

Current state:
- No concrete Lessons connector path yet.

Impact:
- Key cross-tool user journey is absent.

### 5) Data model and organization

What SRS asks:
- Rich metadata, categories hierarchy, playlists, course allocations, audit log, quotas, API governance entities.

Current state:
- Minimal model with three core tables.

Impact:
- Many required capabilities cannot be built without schema evolution.

## Recommended implementation roadmap to close SRS gaps

### Wave 1 (P1 foundation)

- Introduce lifecycle/state model and visibility scope fields.
- Add storage location and external platform identity fields.
- Build external platform config entities and secure credential storage pattern.
- Implement API foundation `/api/v1`, OAuth2 client model, OpenAPI, health endpoint.

### Wave 2 (P1 business capabilities)

- Implement manage endpoints for existing videos (metadata/visibility/status/schedule).
- Implement private-channel upload adapters for YouTube and Vimeo.
- Implement privacy synchronization and retry handling.
- Implement signed embed URL generation service and endpoint.

### Wave 3 (P1/P2 integration)

- Lessons picker integration and promote flow.
- Add category hierarchy + course allocations and API exposure.
- Extend search/facets to metadata and captions.

### Wave 4 (P2 operational maturity)

- Notifications, webhook subsystem, API client usage dashboard.
- Platform health monitoring and link validation cron.
- Instructor analytics expansion and student progress/resume.

### Wave 5 (NFR/compliance hardening)

- Performance SLA validation harness and load tests.
- Accessibility audit and remediation.
- Privacy/compliance controls (PII minimization, exports/deletions, youtube-nocookie handling, etc.).

## Immediate next-step checklist (execution-focused)

1. Data model extension proposal for P1 entities and fields.
2. API contract-first design for `/api/v1` aligned to FR-120..126, FR-131.
3. OAuth2 client management model and admin UI skeleton.
4. External connector abstraction with `YouTubeConnector` and `VimeoConnector` first iteration.
5. Lessons integration design spike defining minimal P1 cut.
6. Test strategy upgrade: API contract tests, connector mocks, and expanded e2e matrix.

## Notes on deferred scope

Per SRS v3.0, these are intentionally future-phase and should not block P1 compliance:
- FR-011 Bulk upload.
- FR-095 Video comments/discussion.

---

Document version: 1.0
Generated from repository state and `Sakai_VTM_Requirements_SRS_v3.0.pdf` analysis on 2026-03-28.
