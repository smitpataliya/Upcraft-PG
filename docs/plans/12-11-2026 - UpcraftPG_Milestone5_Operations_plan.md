# Upcraft PG Backend — Milestone 5: Operations (Complaints, Announcements, Food Menu, Private Documents, Manager Tasks & Notifications) Plan

## Status

Approved

## Module Overview

Implements Milestone 5 daily PG operations across `Upcraft-PG`, `Upcraft-Shree-krishna-pg-frontend`, and `Upcraft-PG-mobile` using **Option A** (evolve existing legacy operational tables via Flyway `V13` + expose multi-tenant `/api/v1/*` endpoints + `@Async` `NotificationChannelProvider` boundary while keeping legacy `/api/*` endpoints functional):
- **Complaints & Maintenance**: Multi-status lifecycle (`SUBMITTED`, `ACKNOWLEDGED`, `ASSIGNED`, `IN_PROGRESS`, `WAITING_FOR_TENANT`, `RESOLVED`, `CLOSED`, `REOPENED`), category (`PLUMBING`, `ELECTRICAL`, `CLEANING`, `WIFI`, `FURNITURE`, `SECURITY`, `WATER`, `FOOD`, `INTERNET`, `OTHER`), `preferredContactTime`, manager assignment, threaded `complaint_comments` (tenant-visible replies vs staff-only internal notes), private `complaint_attachments` backed by `stored_files`, response/resolution timestamps, and resident reopen policy.
- **Announcements & Read Tracking**: Organization/property/audience-scoped notices (`ALL_PROPERTIES`, `SELECTED_PROPERTY`, `ALL_TENANTS`, `MANAGERS_ONLY`), `publishAt` / `expiresAt`, optional private attachment (`stored_files`), and idempotent per-user read tracking (`announcement_reads`).
- **Food Menu**: Property-scoped daily & weekly meals (`BREAKFAST`, `LUNCH`, `SNACKS`, `DINNER`) with `dayOfWeek` / `dateScheduled`, `veg`, `available`, `published`, and read-only resident viewing (no food ordering).
- **Private Documents**: `documents` metadata registry linking `stored_files` (`FileV1Controller`) to `ORGANIZATION`, `PROPERTY`, `TENANT`, `TENANCY`, or `INVOICE_PAYMENT` targets with category (`AGREEMENT`, `IDENTITY_DOCUMENT`, `MOVE_IN_OUT_CHECKLIST`, `RECEIPT`, `DEPOSIT_STATEMENT`, `PROPERTY_DOCUMENT`, `OTHER`), `sharedWithTenant` flag, and strict download authorization inside `StoredFileServiceImpl`.
- **Manager Tasks**: Property-scoped task checklist (`manager_tasks`) assigned to managers (standalone or linked to complaints), filtered by assigned property and status (`OPEN`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`).
- **Notifications & Push**: Organization/property-aware `/api/v1/notifications` with unread count, mark-read, mark-all-read, per-user channel preferences (`notification_preferences`), Expo push token registration (`push_tokens`), and bounded `@Async` dispatch via `NotificationChannelProvider` adapters (`ExpoPushNotificationProvider`, `EmailNotificationProvider`, `SmsWhatsAppNotificationProvider`) persisting honest `notification_deliveries` statuses (`SENT`, `SKIPPED_UNCONFIGURED`, `FAILED`).

Out of scope: food ordering/payments, real WhatsApp Business / Twilio / Razorpay live credentials (only clean provider configuration boundaries that never fake delivery).

## Tech Stack

- **Backend**: Java 21, Spring Boot 4.1.0, MySQL 8+, Flyway (`V13`), Spring Data JPA (`@EntityGraph` + `JpaSpecificationExecutor`), Jakarta Validation, Caffeine, Spring Mail (`JavaMailSender`), Java `HttpClient` (Expo Push API), bounded `ThreadPoolTaskExecutor`, SLF4J.
- **Web**: React 19, TypeScript, TanStack Start/Router, Tailwind 4, Radix UI, TanStack Query, RHF + Zod.
- **Mobile**: React Native, Expo SDK 58, TypeScript, Expo Router, TanStack Query, RHF + Zod, `expo-notifications`.

## Requirements

### Version 1 - [Date: 12-11-2026]

1. Tenant submits complaint with category, subject/title, description, priority, optional `preferredContactTime`, and optional uploaded `COMPLAINT_PHOTO` file IDs (`stored_files`).
2. Owner or assigned Manager (`COMPLAINTS`) assigns complaint, transitions across `SUBMITTED -> ACKNOWLEDGED -> ASSIGNED -> IN_PROGRESS -> WAITING_FOR_TENANT -> RESOLVED -> CLOSED -> REOPENED`, adds public replies or internal notes (`isInternal = true`), and records resolution summary. Tenant sees only own complaints and public comments, and can reply or reopen `RESOLVED`/`CLOSED` complaints.
3. Owner or assigned Manager (`NOTICES`) publishes/updates/archives announcements scoped to org/property/audience with `publishAt`, `expiresAt`, and optional private attachment; tenants view active property notices and mark them read idempotently (`announcement_reads`).
4. Owner or assigned Manager (`NOTICES`) manages property-scoped weekly food menu (`BREAKFAST`, `LUNCH`, `SNACKS`, `DINNER`); tenants view today/weekly menu (read-only).
5. Private documents link `stored_files` to org/property/tenant/tenancy/invoice-payment entities; downloads check `StoredFileServiceImpl` authorization (tenants only access `sharedWithTenant = true` documents for their own `tenantId`/`tenancyId` or their own uploads; managers only assigned properties; owners full org).
6. Owner/Manager manages property-scoped `manager_tasks`; managers only see/update tasks on assigned properties.
7. Notifications cover complaint updates, announcements, documents, invoices, payments, electricity bills, invitations, and move-in/out reminders; users manage preferences and register/deactivate Expo push tokens (`POST /api/v1/push-tokens`). External providers execute asynchronously and mark `SKIPPED_UNCONFIGURED` when credentials/tokens are missing.
8. Legacy `/api/complaints`, `/api/announcements`, `/api/menu`, and `/api/notifications` remain backward-compatible.

## Non-Functional Requirements

- **Expected Scale (ASSUMPTION)**: 50–500 concurrent users, up to ~200 properties, ~50,000 complaints, ~100,000 comments, ~20,000 documents, ~250,000 notifications over 1–2 years; average 5–15 req/s, peak 40 req/s; read/write ratio ~80/20.
- **Latency Targets (estimate)**:
  - Paginated operational lists (`/api/v1/complaints`, `/api/v1/announcements`, `/api/v1/documents`, `/api/v1/manager-tasks`, `/api/v1/notifications`): `p95 <= 200 ms`.
  - Weekly food menu / unread notification count: `p95 <= 100 ms`.
  - Complaint/announcement/task/document write + async notification trigger: `p95 <= 350 ms`.
- **Consistency & Timezone**: ACID `@Transactional` writes for domain state + in-app notification persistence; external push/SMTP/SMS runs `@Async` after commit (`TransactionSynchronizationManager` / separate async call). Business dates in `Asia/Kolkata`, timestamps in UTC.

## Options Considered

| Option | Pattern | DB Queries / Path | 10x / 100x Load | Effort | Fit | Main Risk |
|---|---|---|---|---|---|---|
| **Option A (Chosen)** | Evolve existing tables (`V13`) + `/api/v1/*` + `@Async` `NotificationChannelProvider` | 1–2 indexed queries (`O(k)` paged) | Indexes + pagination cap + bounded pool | **M** | High | Legacy backfill edge cases |
| Option B | Separate parallel `op_*` tables leaving legacy frozen | 1–2 indexed queries | Same DB cost | M–L | Low | Split-brain data drift |
| Option C | Transactional Outbox + worker/Kafka queue | 2 writes + worker | Best burst fan-out | L | Medium | Over-engineered for current stack |

**Recommendation:** Option A — single source of truth across legacy & v1 callers, matches `V2`/`V4` backfill conventions, isolates slow external calls via bounded `ThreadPoolTaskExecutor`.

## Architecture & Design

### Backend Packages (`com.upcraft.stay.operations.*` + `storage` updates)
- `operations/entity/`:
  - `ComplaintCommentEntity`, `ComplaintAttachmentEntity`
  - `AnnouncementReadEntity`
  - `DocumentEntity`
  - `ManagerTaskEntity`
  - `PushTokenEntity`, `NotificationPreferenceEntity`, `NotificationDeliveryEntity`
  - Extend legacy `Complaint`, `Announcement`, `Meal`, `Notification` (or add `V13` columns + JPA mappings preserving legacy fields while adding `organization`, `property`, `version`, and M5 fields).
- `operations/enums/`: `ComplaintAudienceType`, `DocumentScopeType`, `DocumentCategory`, `ManagerTaskStatus`, `ManagerTaskPriority`, `NotificationChannel`, `NotificationDeliveryStatus`, `NotificationEventType`.
- `operations/repository/`: `@EntityGraph` + `JpaSpecificationExecutor` repositories for bounded paginated queries without N+1.
- `operations/service/` + `impl/`:
  - `ComplaintV1Service` / `ComplaintV1ServiceImpl`
  - `AnnouncementV1Service` / `AnnouncementV1ServiceImpl`
  - `FoodMenuV1Service` / `FoodMenuV1ServiceImpl`
  - `DocumentV1Service` / `DocumentV1ServiceImpl`
  - `ManagerTaskV1Service` / `ManagerTaskV1ServiceImpl`
  - `NotificationV1Service` / `NotificationV1ServiceImpl`
  - `NotificationDispatchService` (dispatches in-app + `@Async` external channels via `List<NotificationChannelProvider>`)
  - Providers: `ExpoPushNotificationProvider`, `EmailNotificationProvider`, `SmsWhatsAppNotificationProvider`
- `operations/controller/`: `ComplaintV1Controller`, `AnnouncementV1Controller`, `FoodMenuV1Controller`, `DocumentV1Controller`, `ManagerTaskV1Controller`, `NotificationV1Controller`.
- `common/config/AsyncConfig.java`: `@EnableAsync` + bounded `ThreadPoolTaskExecutor` (`notificationExecutor`: core=4, max=16, queue=200, `CallerRunsPolicy` or abort+log).

## API Endpoints

All endpoints wrap responses in `ApiResponseDTO<T>`, paginate lists via `PaginationUtils` (`default=10`, `max=100`), and enforce `@PreAuthorize` + `AccessGuardService`.

| Method & Path | Roles / Guard | Request DTO | Response / Status | Target p95 |
|---|---|---|---|---|
| `GET /api/v1/complaints` | Owner / Manager (`READ`) | Filters (`propertyId`, `status`, `category`, `priority`, `assignedToUserId`, `search`, page/sort) | `200 OK` `Page<ComplaintV1ResponseDTO>` | `<= 200ms` |
| `GET /api/v1/complaints/mine` | `PG_MEMBER` | Pagination | `200 OK` `Page<ComplaintV1ResponseDTO>` | `<= 150ms` |
| `POST /api/v1/complaints` | `PG_MEMBER` (active/past tenancy) | `CreateComplaintV1RequestDTO` | `201 Created` `ComplaintV1ResponseDTO` | `<= 300ms` |
| `GET /api/v1/complaints/{id}` | Owner / Manager / owning Member | — | `200 OK` `ComplaintDetailV1ResponseDTO` (comments filtered for member) | `<= 150ms` |
| `PATCH /api/v1/complaints/{id}/status` | Owner / Manager (`COMPLAINTS`) / Member (`REOPENED` only) | `UpdateComplaintStatusV1RequestDTO` | `200 OK` `ComplaintV1ResponseDTO` | `<= 250ms` |
| `PATCH /api/v1/complaints/{id}/assign` | Owner / Manager (`COMPLAINTS`) | `AssignComplaintRequestDTO` | `200 OK` `ComplaintV1ResponseDTO` | `<= 250ms` |
| `POST /api/v1/complaints/{id}/comments` | Owner / Manager (`COMPLAINTS`) / owning Member | `CreateComplaintCommentRequestDTO` | `201 Created` `ComplaintCommentResponseDTO` | `<= 250ms` |
| `GET /api/v1/announcements` | Authenticated (scoped by org/property/active) | Filters (`propertyId`, `activeOnly`, page) | `200 OK` `Page<AnnouncementV1ResponseDTO>` | `<= 200ms` |
| `POST /api/v1/announcements` | Owner / Manager (`NOTICES`) | `UpsertAnnouncementV1RequestDTO` | `201 Created` `AnnouncementV1ResponseDTO` | `<= 350ms` |
| `PUT /api/v1/announcements/{id}` | Owner / Manager (`NOTICES`) | `UpsertAnnouncementV1RequestDTO` | `200 OK` `AnnouncementV1ResponseDTO` | `<= 250ms` |
| `POST /api/v1/announcements/{id}/read` | Authenticated | — | `200 OK` `AnnouncementReadResponseDTO` | `<= 100ms` |
| `DELETE /api/v1/announcements/{id}` | Owner / Manager (`NOTICES`) | — | `204 No Content` | `<= 200ms` |
| `GET /api/v1/food-menu/weekly` | Authenticated | `propertyId`, `startDate` | `200 OK` `List<MealV1ResponseDTO>` | `<= 100ms` |
| `POST /api/v1/food-menu` | Owner / Manager (`NOTICES`) | `UpsertMealV1RequestDTO` | `201 Created` `MealV1ResponseDTO` | `<= 250ms` |
| `PUT /api/v1/food-menu/{id}` | Owner / Manager (`NOTICES`) | `UpsertMealV1RequestDTO` | `200 OK` `MealV1ResponseDTO` | `<= 200ms` |
| `DELETE /api/v1/food-menu/{id}` | Owner / Manager (`NOTICES`) | — | `204 No Content` | `<= 200ms` |
| `GET /api/v1/documents` | Owner / Manager (`READ`) / Member (`mine` or shared) | Filters (`propertyId`, `scopeType`, `category`, `tenantId`, `tenancyId`, page) | `200 OK` `Page<DocumentResponseDTO>` | `<= 200ms` |
| `POST /api/v1/documents` | Owner / Manager / Member (own tenant scope) | `CreateDocumentRequestDTO` (links `storedFileId`) | `201 Created` `DocumentResponseDTO` | `<= 250ms` |
| `DELETE /api/v1/documents/{id}` | Owner / Manager | — | `204 No Content` (soft delete) | `<= 200ms` |
| `GET /api/v1/manager-tasks` | Owner / Manager | Filters (`propertyId`, `status`, `assignedToUserId`, page) | `200 OK` `Page<ManagerTaskResponseDTO>` | `<= 200ms` |
| `POST /api/v1/manager-tasks` | Owner / Manager | `UpsertManagerTaskRequestDTO` | `201 Created` `ManagerTaskResponseDTO` | `<= 250ms` |
| `PATCH /api/v1/manager-tasks/{id}/status` | Owner / assigned Manager | `UpdateManagerTaskStatusRequestDTO` | `200 OK` `ManagerTaskResponseDTO` | `<= 200ms` |
| `GET /api/v1/notifications` | Authenticated | `unreadOnly`, page | `200 OK` `Page<NotificationV1ResponseDTO>` | `<= 150ms` |
| `GET /api/v1/notifications/unread-count` | Authenticated | — | `200 OK` `UnreadCountResponseDTO` | `<= 80ms` |
| `PATCH /api/v1/notifications/{id}/read` | Authenticated (owner) | — | `200 OK` `Void` | `<= 100ms` |
| `PATCH /api/v1/notifications/read-all` | Authenticated | — | `200 OK` `Void` | `<= 150ms` |
| `GET /api/v1/notifications/preferences` | Authenticated | — | `200 OK` `NotificationPreferenceResponseDTO` | `<= 100ms` |
| `PUT /api/v1/notifications/preferences` | Authenticated | `UpdateNotificationPreferenceRequestDTO` | `200 OK` `NotificationPreferenceResponseDTO` | `<= 150ms` |
| `POST /api/v1/push-tokens` | Authenticated | `RegisterPushTokenRequestDTO` | `200 OK` `PushTokenResponseDTO` | `<= 150ms` |
| `DELETE /api/v1/push-tokens` | Authenticated | `token` query param | `204 No Content` | `<= 150ms` |

## Database Design

**`V13__milestone5_operations_tables.sql`**:
1. Evolve `complaints`: add `organization_id`, `property_id`, `tenant_id`, `tenancy_id`, `room_id`, `bed_id`, `assigned_to_user_id`, `preferred_contact_time`, `resolution_summary`, `acknowledged_at`, `resolved_at`, `closed_at`, `version`; backfill `organization_id`/`property_id` from member tenancy or default org/property; index `(organization_id, property_id, status, created_at)` and `(assigned_to_user_id, status)`.
2. `complaint_comments`: `id`, `organization_id`, `complaint_id`, `author_user_id`, `message`, `is_internal BIT(1)`, plus 8 audit columns + `version`; index `(complaint_id, created_at)`.
3. `complaint_attachments`: `id`, `organization_id`, `complaint_id`, `comment_id` (nullable), `stored_file_id`, plus 8 audit columns + `version`; index `(complaint_id)`.
4. Evolve `announcements`: add `organization_id`, `property_id` (nullable for org-wide), `audience_type`, `publish_at`, `expires_at`, `attachment_file_id`, `version`; backfill org; index `(organization_id, property_id, pinned, created_at)`.
5. `announcement_reads`: `id`, `organization_id`, `announcement_id`, `user_id`, `read_at`, 8 audit columns + `version`, `UNIQUE(announcement_id, user_id)`.
6. Evolve `meals`: add `organization_id`, `property_id`, `day_of_week`, `published BIT(1)`, `version`; backfill org/property; index `(property_id, date_scheduled, available)`.
7. `documents`: `id`, `organization_id`, `property_id`, `stored_file_id`, `scope_type`, `document_category`, `title`, `description`, `tenant_id`, `tenancy_id`, `invoice_id`, `payment_id`, `shared_with_tenant BIT(1)`, `uploaded_by_user_id`, 8 audit columns + `version`; indexes `(organization_id, scope_type, document_category)`, `(tenant_id, shared_with_tenant)`, `(property_id, created_at)`.
8. `manager_tasks`: `id`, `organization_id`, `property_id`, `assigned_to_user_id`, `created_by_user_id`, `complaint_id` (nullable), `title`, `description`, `priority`, `task_status`, `due_at`, `completed_at`, `completion_notes`, 8 audit columns + `version`; index `(property_id, task_status, due_at)`, `(assigned_to_user_id, task_status)`.
9. Evolve `notifications`: add `organization_id`, `property_id`, `event_type`, `reference_type`, `reference_id`, `read_at`, `version`; backfill org from user's org; index `(user_id, is_read, created_at)`.
10. `push_tokens`: `id`, `organization_id`, `user_id`, `token`, `platform`, `device_name`, `active BIT(1)`, `last_seen_at`, 8 audit columns + `version`, `UNIQUE(token)`.
11. `notification_preferences`: `id`, `organization_id`, `user_id`, `in_app_enabled`, `push_enabled`, `email_enabled`, `sms_enabled`, `whatsapp_enabled`, `digest_quiet_hours`, 8 audit columns + `version`, `UNIQUE(user_id)`.
12. `notification_deliveries`: `id`, `organization_id`, `notification_id`, `user_id`, `channel`, `delivery_status`, `provider_name`, `provider_message_id`, `error_message`, `attempted_at`, 8 audit columns + `version`; index `(notification_id, channel)`.

## Performance & Complexity Analysis

| Endpoint | DB Queries | Time Complexity | Index Used | Max Payload | Target p95 | Risk |
|---|---|---|---|---|---|---|
| `GET /api/v1/complaints` | 2 (paged count + `@EntityGraph` fetch) | `O(k)` (`k <= 100`) | `idx_complaints_org_prop_status` | ~120 KB | `<= 200ms` | Filter spec join bloat (keep spec predicates flat) |
| `GET /api/v1/complaints/{id}` | 3 (complaint + comments + attachments) | `O(c + a)` bounded | `idx_cc_complaint_created` | ~100 KB | `<= 150ms` | Large comment threads (cap/page if needed) |
| `POST /api/v1/announcements` | 2–3 + batch insert (`saveAll` chunked) | `O(r)` recipients (`<= 1000`/org) | `idx_om_org_role` | ~20 KB | `<= 350ms` | Large tenant broadcast -> chunked inserts + `@Async` external push |
| `POST /api/v1/announcements/{id}/read` | 2 (lookup + idempotent insert) | `O(1)` | `uk_announcement_read_user` | ~2 KB | `<= 100ms` | Concurrent double-tap -> catch `DataIntegrityViolationException` cleanly |
| `GET /api/v1/documents` | 2 (paged count + fetch) | `O(k)` (`k <= 100`) | `idx_docs_org_scope_cat` | ~80 KB | `<= 200ms` | Binary streaming isolated to `/api/v1/files/{id}/content` |
| `GET /api/v1/notifications/unread-count` | 1 (`COUNT`) | `O(1)` index scan | `idx_v2_notifications_user_read_created` | < 1 KB | `<= 80ms` | High poll rate -> Caffeine short TTL or ETag + invalidation on write |

## Scalability & Load Plan

- **Little's Law**:
  - Expected load (15 req/s, avg 40 ms): `L = 15 * 0.04 = 0.6` concurrent requests (well inside Tomcat 200 threads & HikariCP 10–20 connections).
  - 10x peak (150 req/s, avg 60 ms): `L = 150 * 0.06 = 9.0` concurrent requests; HikariCP (20 conns) + composite indexes absorb load; `@Async` external HTTP/SMTP calls offloaded to `notificationExecutor` (core=4, max=16, queue=200) so Tomcat request threads never block on Expo/SMTP timeouts.
  - Burst (1M requests): `RateLimitFilter` sheds excess traffic fast with `429 Too Many Requests`, `PaginationUtils` enforces `size <= 100`, bounded `notificationExecutor` queue prevents OOM (`CallerRunsPolicy`/reject+log for non-critical external push), HikariCP `connection-timeout` fails fast with `503`.
- **Memory**: All list endpoints bounded (`size <= 100`), binary files streamed via `Resource` (never `byte[]` in heap), batch notifications chunked (`100` per batch), bounded Caffeine caches (`maximumSize=1000`, `expireAfterWrite=30s`).

## Security & Risks

- **Authorization**: Every `/api/v1/*` endpoint enforces `@PreAuthorize` + `AccessGuardService` org/property/tenant checks. `PG_MEMBER` cannot view `isInternal = true` complaint comments or non-shared (`sharedWithTenant = false`) documents. `MANAGER` cannot access unassigned properties or properties lacking `canHandleComplaints` / `canPublishNotices`.
- **Private Files**: `StoredFileServiceImpl` updated so complaint photos and shared documents authorize owning resident, assigned manager (`READ`), and org owner, never returning public URLs.
- **Honest External Providers**: `ExpoPushNotificationProvider`, `EmailNotificationProvider`, and `SmsWhatsAppNotificationProvider` record `SKIPPED_UNCONFIGURED` when credentials/tokens are absent — never marking `SENT` falsely.
- **No PII in Logs**: Log only UUIDs, counts, and status codes; never log emails, phone numbers, tokens, or file contents.

## Test & Load Verification

- Unit tests: Complaint state transitions & reopen policy, internal comment filtering, document share authorization, manager task scoping, notification provider `SKIPPED_UNCONFIGURED` vs `SENT` behavior.
- Integration tests (`MockMvc` + H2): Cross-org / cross-property / cross-tenant rejection (`403`), manager permission checks (`canHandleComplaints`, `canPublishNotices`), idempotent `announcement_reads`, `push-tokens` registration, end-to-end complaint -> comment -> photo -> resolve -> reopen flow.
- MySQL migration verification: `FlywayMySqlIntegrationTest` (`V1`–`V13` fresh & legacy baseline + Hibernate `ddl-auto=validate`).
- Load plan (manual/Ask First if tool run): k6 50 VUs for 2m against `/api/v1/complaints`, `/api/v1/announcements`, `/api/v1/notifications`, pass criteria `p95 <= 200ms`, 0% 5xx.

## Rollout & Rollback

- **Rollout**: Apply `V13` via Flyway on boot (`ddl-auto=validate`), deploy backend, deploy Web & Mobile clients.
- **Rollback**: Revert app jar to M4; `V13` only adds nullable/defaulted columns and new tables, so M4 entities remain compatible.

## Open Questions

- None blocking (proceeding with Option A defaults; `FlywayMySqlIntegrationTest` + Mobile `expo-image-picker` install require user terminal/package approval when executed).

## Implementation Checklist

- [ ] Flyway `V13__milestone5_operations_tables.sql` + SQL verification doc
- [ ] Backend enums, entities, repositories, specs, mappers, DTOs (`operations.*`)
- [ ] `StoredFileServiceImpl` read authorization extension for `COMPLAINT_PHOTO` & `DocumentEntity`
- [ ] `AsyncConfig`, `NotificationChannelProvider` adapters, `NotificationDispatchService`
- [ ] Services & `/api/v1/*` controllers (`Complaints`, `Announcements`, `FoodMenu`, `Documents`, `ManagerTasks`, `Notifications`, `PushTokens`)
- [ ] Unit + `Milestone5OperationsIntegrationTest` + `FlywayMySqlIntegrationTest` update
- [ ] Web frontend (`Upcraft-Shree-krishna-pg-frontend`) Milestone 5 `/api/v1/*` services, components, routes & nav
- [ ] Mobile (`Upcraft-PG-mobile`) Milestone 5 `/api/v1/*` services, screens, push registration & tests
- [ ] Full backend/web/mobile build, lint, test & live MySQL verification

## Change History

| Date | Change Description | Old Requirement (Commented) |
|---|---|---|
| 12-11-2026 | Initial Milestone 5 plan created (Option A) | - |

