## Plan: Sakai Video Training Module V1

Build a new Sakai Video Training tool using the Polls Spring MVC + Thymeleaf pattern, with hybrid video hosting (native ContentHostingService uploads + external provider embeds), permission-aware visibility via SecurityService/AuthzGroupService context, Lessons content-picker integration, captions/transcript support, analytics dashboard, and REST APIs. Exclude gradebook integration and playback completion/progress rules.

**Steps**
1. Phase 1: Finalize architecture and module scaffolding
2. Define the module split as `video-training/api`, `video-training/impl`, `video-training/tool`, and web API exposure in existing `webapi` module.
3. Reuse Polls startup/config structure for Spring MVC + Thymeleaf bootstrapping, message source setup, locale handling, and static resource mapping. depends on step 1.
4. Define tool registration, permissions, and service wiring boundaries (service layer handles business rules; visibility is enforced by permission checks in site/group context). depends on step 1.
5. Phase 2: Domain model and persistence
6. Define core entities for video metadata and captions/transcripts, including fields for title, description, provider type (native/external), source reference, release/retract dates, required-view-permission, and ownership. depends on phase 1.
7. Implement repositories and service methods for create/edit/delete/list and filtered retrieval by site and permission + visibility windows. depends on previous step.
8. Add analytics event model and aggregate read-model queries (views and usage counts only; no completion thresholds). parallel with step 6 after schema is stable.
9. Phase 3: Permissions and visibility engine
10. Implement permission resolution and visibility filtering using SecurityService/AuthzGroupService context checks combined with release/retract timing logic modeled after Announcement/Assignment visibility patterns. depends on phase 2.
11. Implement permission matrix (manage/view/analytics/captions) in service methods and controller model flags, ensuring management actions remain permission-gated and independent from any hardcoded role name. depends on previous step.
12. Phase 4: Tool UI (Spring MVC + Thymeleaf)
13. Build controller routes and Thymeleaf pages for: library list, create/edit form, delete flow, details/play view, captions/transcript management, and analytics dashboard. depends on phases 2-3.
14. Align page shell with Sakai tool standards used by Polls: `fragments/common :: head`, `portletBody`, `navIntraTool`, `sak-banner-*`, `includeLatestJQuery`, and iframe height adjustment hooks. depends on previous step.
15. Move tool-specific visual rules into `library/src/skins/default/src/sass/modules/tool/video-training/_video-training.scss` and import from `tool.scss`; keep Thymeleaf templates focused on structure and complex Bootstrap components rather than utility-only class accumulation. depends on previous step.
16. Follow Polls form-binding, flash-message, and localized messaging patterns; include Sakai locale/timezone-aware rendering for date windows. depends on previous step.
17. Implement hybrid source UX: native upload path (ContentHostingService-backed) and external embed path (provider URL/reference) in same create/edit workflow. depends on previous step.
18. Phase 5: Lessons integration (content picker)
19. Add a Lessons integration path so instructors can select a video from the new module through Lessons picker/add-item flow and embed it into Lessons pages. depends on phases 2-4.
20. Store and render Lessons-linked video references without grade passback, without progress syncing, and without completion logic. depends on previous step.
21. Validate display behavior against existing multimedia rendering expectations in Lessons. depends on previous step.
22. Phase 6: REST APIs for mobile/integration
23. Add permission-aware endpoints in `webapi` for listing videos, fetching video details, and retrieving analytics summaries with site/context validation through existing base controller patterns. depends on phases 2-3.
24. Ensure response models include capability flags for client-side behavior (manage/view/analytics permissions) and enforce visibility filtering server-side. depends on previous step.
25. Phase 7: Verification and hardening
26. Add/extend Playwright tests for changed user flows: instructor creates/publishes video, student visibility behavior, TA/admin management access, Lessons embedding path, and analytics view access control. depends on phases 4-6.
27. Add backend tests for visibility filtering by permission and release/retract windows, plus REST authorization behavior.
28. Run module-level and targeted test commands; verify i18n coverage and accessibility checks for key pages.

**Relevant files**
- `source-sakai/polls/tool/src/main/java/org/sakaiproject/poll/tool/config/PollsWebMvcConfig.java` — Spring MVC + Thymeleaf wiring template.
- `source-sakai/polls/tool/src/main/java/org/sakaiproject/poll/tool/config/PollsWebApplicationInitializer.java` — Tool bootstrap pattern.
- `source-sakai/polls/tool/src/main/java/org/sakaiproject/poll/tool/mvc/PollController.java` — Controller route and model population pattern.
- `source-sakai/polls/tool/src/main/webapp/WEB-INF/templates/polls/list.html` — Thymeleaf list rendering pattern.
- `source-sakai/polls/tool/src/main/webapp/WEB-INF/templates/polls/edit.html` — Thymeleaf form-binding and validation pattern.
- `source-sakai/polls/api/src/main/java/org/sakaiproject/poll/api/PollConstants.java` — Permission constants pattern.
- `source-sakai/polls/impl/src/main/java/org/sakaiproject/poll/impl/service/PollsServiceImpl.java` — Service-layer authorization and business-rule template.
- `source-sakai/kernel/api/src/main/java/org/sakaiproject/authz/api/AuthzGroupService.java` — Site/group authorization context support.
- `source-sakai/kernel/api/src/main/java/org/sakaiproject/authz/api/SecurityService.java` — Existing authorization context reference.
- `source-sakai/lessonbuilder/api/src/java/org/sakaiproject/lessonbuildertool/SimplePageItem.java` — Lessons item model and attributes.
- `source-sakai/lessonbuilder/tool/src/java/org/sakaiproject/lessonbuildertool/service/LessonEntity.java` — Lessons content-source integration contract.
- `source-sakai/lessonbuilder/tool/src/java/org/sakaiproject/lessonbuildertool/tool/beans/SimplePageBean.java` — Lessons add/edit backend flow patterns.
- `source-sakai/lessonbuilder/tool/src/java/org/sakaiproject/lessonbuildertool/tool/producers/ShowPageProducer.java` — Lessons render and item-type dispatch behavior.
- `source-sakai/lessonbuilder/tool/src/java/org/sakaiproject/lessonbuildertool/tool/producers/ResourcePickerProducer.java` — Picker integration pattern.
- `source-sakai/conversations/impl/src/main/java/org/sakaiproject/conversations/impl/ConversationsServiceImpl.java` — Analytics aggregation/caching pattern.
- `source-sakai/webapi/src/main/java/org/sakaiproject/webapi/controllers/AbstractSakaiApiController.java` — API site/session validation base.
- `source-sakai/webapi/src/main/java/org/sakaiproject/webapi/controllers/GradesController.java` — Permission-aware REST response/filtering pattern.
- `source-sakai/e2e-tests/src/test/java/org/sakaiproject/e2e/tests/ForumsTest.java` — Multi-role Playwright flow template.
- `source-sakai/e2e-tests/src/test/java/org/sakaiproject/e2e/support/SakaiUiTestBase.java` — Playwright base test infrastructure.

**Verification**
1. Backend unit/integration tests for CRUD, permission-aware visibility filters, release/retract window logic, and captions/transcript persistence.
1. API tests for permission-restricted video list/detail/analytics endpoints across personas with different permission grants.
1. Playwright scenario set in `e2e-tests`: instructor manages video library, student sees only visible videos, TA/admin management checks, Lessons embedding flow.
1. UI shell verification in portal iframe: Video Training must render with Sakai skin/theme, in-tool menu (`navIntraTool`), proper frame height, and consistent banner/button/table styling aligned with Polls.
1. Manual verification of hybrid source flows (native upload and external embed), including failure/validation cases.
1. i18n and timezone checks using Sakai locale/time services for all date/time display fields.
1. Accessibility spot checks on key pages (forms, tables, dialogs, analytics views).

**Decisions**
- Confirmed output: implementation plan only.
- Professional standard requirement: avoid unnecessary Markdown documents and avoid non-professional/helper comments in code and deliverables.
- Documentation policy: create or update documentation files only when explicitly required by implementation, migration, operations, or user request.
- Hosting model: hybrid from day one (native + external provider).
- Permission model in v1: manage/view/analytics/captions permissions (no hardcoded role names in business logic).
- Included v1 capabilities: library management, permission-aware visibility windows, captions/transcripts, analytics dashboard, REST API.
- Explicitly excluded: gradebook integration and playback completion/progress tracking (including any 90% completion contract rules).
- Lessons scope: embed via content picker only; no grade passback or progress reporting.
- Permission approach: SecurityService/AuthzGroupService-centered permission checks in site/group context.
- Frontend structure baseline: mirror Polls Thymeleaf/Sakai shell conventions before introducing visual customizations.
- Styling policy: define tool styles in library SCSS modules and avoid scattering micro-utility classes in templates.

**Further Considerations**
1. Data source for analytics events: Option A existing Sakai event tracking tables, Option B module-specific event table. Recommendation: Option B for predictable query performance and schema control.
2. External provider abstraction depth in v1: Option A generic provider interface with one concrete external adapter, Option B direct URL/embed support only. Recommendation: Option A minimal interface + one adapter to prevent rewrite in v2.
3. Lessons integration mode: Option A new item type for video-training references, Option B reuse MULTIMEDIA with provider metadata attributes. Recommendation: Option B initially for lower migration/rendering risk unless editor UX demands a dedicated item type.