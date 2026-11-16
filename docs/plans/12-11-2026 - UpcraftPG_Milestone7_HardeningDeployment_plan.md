# Upcraft PG Backend - Milestone 7: Hardening, Search, Audit Logs, Docker & Acceptance Suite Plan

## Status

Done

## Module Overview

Implements Milestone 7 across `Upcraft-PG`, finishing production hardening, global cross-module search, immutable audit logging, proxy-safe tiered rate limiting, Actuator health readiness, containerized MySQL + Spring Boot + Web deployment, backup/restore automation & verification, and a 20-scenario automated acceptance suite.

- **In Scope:**
  - Flyway `V15__create_audit_logs_table.sql` + `AuditLogService` + `GET /api/v1/audit-logs`
  - Bounded `GET /api/v1/search?q=` returning max 5 results per category (Tenants, Rooms/Beds, Invoices, Payments, Complaints)
  - Proxy-safe tiered Caffeine `RateLimitFilter` (`auth`, `publicShare`, `uploads`, `sensitiveMutations`)
  - Spring Boot Actuator (`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`)
  - Multi-stage `Dockerfile` (Backend + Web), `docker-compose.yml` (internal MySQL 8 + Backend + Web, persistent volume, non-exposed DB), backup/restore scripts & docs
  - Consolidated 20-Scenario Automated Acceptance Test Suite (`Milestone7AcceptanceSuiteIntegrationTest`) + backend/web/mobile README updates
- **Out of Scope:**
  - Automated push/deployment to remote VPS or mutation of existing `pg_management` production data
  - Distributed Redis rate limiting or Elasticsearch/Kafka CDC (documented as future horizontal-scaling upgrade path)

## Tech Stack

- **Backend:** Java 21, Spring Boot 4.1.0, Spring Data JPA, Flyway, Spring Security, Caffeine Cache, OpenPDF, Apache Commons, Spring Boot Actuator, JUnit 5, Mockito, MockMvc
- **Database:** MySQL 8+ (`BINARY(16)` UUID PKs, `DATETIME(6)` UTC timestamps, InnoDB, `utf8mb4_unicode_ci`)
- **Containers:** Docker, Docker Compose v2, Eclipse Temurin 21 JRE Alpine, MySQL 8.0, Node 20 Alpine

## Requirements

### Version 1 - [Date: 12-11-2026]

1. **Audit Logs (`V15`):**
   - `AuditLogService` explicitly called on critical security, financial, tenancy, electricity, operational, and share-link mutations inside the same `@Transactional` boundary so critical business writes and audit logs commit or roll back together cleanly.
   - Immutable to application users: zero update/delete endpoints or mutations; `audit_logs` stores `organization_id`, nullable `property_id`, `actor_user_id`, `actor_email`, `actor_role`, `action`, `entity_type`, `entity_id`, `summary`, safe allowlisted `metadata_json`, `client_ip`, `trace_id`, plus the 8 mandatory audit columns + `version`.
   - Strict PII/secret hygiene: never store passwords, OTPs, JWTs, card/gateway secrets, or unnecessary PII in `summary` or `metadata_json`.
   - Authorization: `GET /api/v1/audit-logs` accessible only to `PG_OWNER` (org-scoped) and `MANAGER` (restricted to `AccessGuardService.resolveAccessiblePropertyIds`); `PG_MEMBER` receives `403 Forbidden`.
2. **Global Search (`GET /api/v1/search?q=`):**
   - Bounded to `max 5` items per category: `tenants`, `roomsAndBeds`, `invoices`, `payments`, `complaints`.
   - Uses [`AccessGuardService`](file:///d:/Upcraft/workspace/Upcraft-PG/src/main/java/com/upcraft/stay/security/AccessGuardService.java):
     - `PG_OWNER`: all org properties or optional `propertyId` filter.
     - `MANAGER`: only assigned properties (`resolveAccessiblePropertyIds`).
     - `PG_MEMBER`: restricted strictly to own tenant/residency/invoice/payment/complaint records (never other residents' records or PG-wide directory).
   - Never returns soft-deleted (`status = 9` / `record_status = 9`) rows; stable deterministic ordering (`ORDER BY ... ASC/DESC`).
3. **Rate Limiting Hardening:**
   - Extend [`RateLimitFilter`](file:///d:/Upcraft/workspace/Upcraft-PG/src/main/java/com/upcraft/stay/common/filter/RateLimitFilter.java) with bounded Caffeine buckets (`maximumSize=10000`, 60s window) across separate tiers:
     - `auth`: `/api/v1/auth/*`, `/api/v1/invitations/accept`, `/api/auth/*` (default `30/min`)
     - `publicShare`: `/api/v1/public/share/**` (default `60/min`)
     - `uploads`: `/api/v1/files/upload` (default `20/min`)
     - `sensitive`: `/api/v1/share-links`, `/api/v1/reports/*/export` (default `30/min`)
   - Fix `X-Forwarded-For` spoofing: trust `X-Forwarded-For` / `X-Real-IP` ONLY when `request.getRemoteAddr()` matches `app.rate-limit.trusted-proxies` (default loopback/private proxy ranges, externalizable); otherwise use `request.getRemoteAddr()`.
   - Return `429 Too Many Requests` (`ErrorCode.RATE_LIMIT_EXCEEDED`, `Retry-After: 60`, traceId).
   - Explicitly document in README & plans that Caffeine limits are JVM-instance-local (Redis/Bucket4j required for multi-replica horizontal scaling).
4. **Docker, Health Checks & Backup/Restore:**
   - Add `spring-boot-starter-actuator` (`GET /actuator/health` public liveness/readiness; detailed metrics restricted).
   - Add `Upcraft-PG/Dockerfile`, `Upcraft-Shree-krishna-pg-frontend/Dockerfile`, root `docker-compose.yml` (`mysql`, `backend`, `web`), `.dockerignore`, `.env.docker.example`.
   - Internal MySQL network (`mysql` has NO published host `ports:` in production compose; persistent `mysql_data` & `uploads_data` volumes; healthchecks + `restart: unless-stopped`).
   - Add non-destructive backup (`scripts/backup-mysql.sh` / `.ps1`) and isolated restore verification (`scripts/restore-mysql.sh` / `.ps1` targeting scratch/target schema without wiping live DB).
5. **Acceptance & Load Verification:**
   - Consolidate all **20 Required Acceptance Tests** cleanly (covering auth, hierarchy, manager scoping, occupancy uniqueness, idempotent billing, tenant isolation, partial payments, deposit reconciliation, electricity tariff snapshot, complaints, announcements, reports reconciliation, CSV/PDF exports, cross-tenant/property rejection, move-out settlement, rate limiting, audit immutability, global search scoping, migration/backup safety, build verification).
   - Provide documented k6/Hey load-test script & pass criteria (Ask First before execution).

## Non-Functional Requirements

- **Expected Scale (ASSUMPTION):** Up to ~5,000 registered users portfolio-wide, average `20 RPS`, peak `200 RPS`.
- **Design Targets (TARGET, unmeasured until load test):**
  - `GET /api/v1/search?q=`: p95 `< 120ms`
  - `GET /api/v1/audit-logs`: p95 `< 100ms`
  - `GET /actuator/health`: p95 `< 25ms`
- **Pagination Cap:** Strict `1 <= size <= 100` (`defaultSize = 10`).
- **Consistency:** Strong ACID consistency for financial/tenancy mutations + same-transaction `audit_logs` persistence.

## Options Considered

| Option | Pattern | Speed & DB Queries | 10x / 100x Load Behavior | Effort | Decision |
|---|---|---|---|---|---|
| **A (Chosen)** | Explicit `AuditLogService` + `V15` + Bounded JPQL Search (`/api/v1/search`) + Proxy-safe Tiered Caffeine RateLimit | 1 `INSERT` per audited write; max 5-6 `LIMIT 5` indexed queries on search | Caffeine bounded (`10k` keys, fails fast `429`); HikariCP safe | M | **Chosen** |
| **B** | Hibernate Envers `*_AUD` + Denormalized `search_index` | Extra `REVINFO` + `*_AUD` writes everywhere; 1 search query | High write amplification; PII drift risk | L | Rejected |
| **C** | AOP `@Aspect` Reflection + Lucene Disk Index | Reflection overhead + disk locks | Fails stateless containers | L | Rejected |
| **D** | Debezium CDC + Elasticsearch + Redis | ES search fast; extra network hops | Over-engineered stack for single-PG SaaS | XL | Rejected |

## Architecture & Design

### New Packages & Classes (`com.upcraft.stay.*`)
1. **Audit Module (`com.upcraft.stay.audit.*`):**
   - `enums.AuditAction` (`LOGIN_SUCCESS`, `LOGIN_FAILED`, `USER_REGISTERED`, `INVITATION_CREATED`, `INVITATION_ACCEPTED`, `PROPERTY_CREATED`, `PROPERTY_ARCHIVED`, `TENANCY_MOVE_IN`, `TENANCY_TRANSFER`, `TENANCY_MOVE_OUT`, `INVOICE_GENERATED`, `INVOICE_STATUS_UPDATED`, `PAYMENT_RECORDED`, `PAYMENT_REVERSED`, `DEPOSIT_TRANSACTION_RECORDED`, `ELECTRICITY_READING_APPROVED`, `ELECTRICITY_READING_REJECTED`, `COMPLAINT_STATUS_UPDATED`, `DOCUMENT_UPLOADED`, `SHARE_LINK_CREATED`, `SHARE_LINK_REVOKED`)
   - `enums.AuditEntityType` (`USER`, `INVITATION`, `PROPERTY`, `TENANCY`, `INVOICE`, `PAYMENT`, `DEPOSIT`, `METER_READING`, `COMPLAINT`, `DOCUMENT`, `SHARE_LINK`)
   - `entity.AuditLogEntity` (`extends AuditableEntity`)
   - `repository.AuditLogRepository` (`findAccessibleAuditLogs(...)` JPQL with `Specification` or nullable filter flags, `EntityGraph` on `property`)
   - `dto.AuditLogResponseDTO` (`record`: `id`, `organizationId`, `propertyId`, `propertyName`, `actorUserId`, `actorEmail`, `actorRole`, `action`, `entityType`, `entityId`, `summary`, `metadata`, `clientIp`, `traceId`, `createdAt`)
   - `service.AuditLogService` + `service.impl.AuditLogServiceImpl` (`recordEvent(...)`, `sanitizeMetadata(...)`, `listAuditLogs(...)` read-only paginated max 100)
   - `controller.AuditLogV1Controller` (`GET /api/v1/audit-logs` annotated `@PreAuthorize("hasAnyRole('PG_OWNER', 'MANAGER')")`)
2. **Global Search Module (`com.upcraft.stay.search.*`):**
   - `enums.SearchCategory` (`TENANT`, `ROOM_BED`, `INVOICE`, `PAYMENT`, `COMPLAINT`)
   - `dto.SearchResultItemDTO` (`record`: `id`, `category`, `title`, `subtitle`, `propertyId`, `propertyName`, `routeHint`, `badge`)
   - `dto.GlobalSearchResponseDTO` (`record`: `query`, `tenants`, `roomsAndBeds`, `invoices`, `payments`, `complaints`, `totalHits`)
   - `service.GlobalSearchService` + `service.impl.GlobalSearchServiceImpl` (role-aware query dispatch bounded `PageRequest.of(0, 5)` per category; uses MDC-safe `q` min length 2, max 100)
   - `controller.GlobalSearchV1Controller` (`GET /api/v1/search`)
3. **Hardening & Config (`com.upcraft.stay.common.*`, `security.*`):**
   - Update `AppProperties.RateLimit` (`authRequestsPerMinute=30`, `publicShareRequestsPerMinute=60`, `uploadRequestsPerMinute=20`, `exportRequestsPerMinute=30`, `trustedProxies=List.of(...)`)
   - Update `RateLimitFilter` (multi-tier prefix matching + trusted proxy CIDR/IP validator via `SubnetUtils`/exact CIDR matcher, no external dep needed using `InetAddress` prefix bits)
   - Wire `AuditLogService.recordEvent` into existing auth/property/tenancy/invoice/payment/deposit/electricity/complaint/document/share-link services using `Propagation.MANDATORY` or `REQUIRED` (same transaction!) with clean helper `AuditSanitizer` stripping disallowed keys (`password`, `token`, `otp`, `secret`, `authorization`, `phone`, etc. unless explicit non-secret context).
   - Enable `spring-boot-starter-actuator` (`/actuator/health` permitted in `SecurityConfig`, health probes configured in `application.properties`).

## API Endpoints

| Method | Path | Auth / Role | Request / Query | Response (`ApiResponseDTO`) | Status & Errors | Target p95 |
|---|---|---|---|---|---|---|
| `GET` | `/api/v1/search` | `PG_OWNER`, `MANAGER`, `PG_MEMBER` | `?q=&propertyId=` (`q` 2..100 chars) | `GlobalSearchResponseDTO` (<=5 items/bucket) | `200`, `400`, `401`, `403` | `< 120ms` (TARGET) |
| `GET` | `/api/v1/audit-logs` | `PG_OWNER`, `MANAGER` | `?propertyId=&action=&entityType=&fromDate=&toDate=&page=0&size=20` | `PageResponseDTO<AuditLogResponseDTO>` | `200`, `400` (`size>100`), `403` (`PG_MEMBER` or cross-org/prop) | `< 100ms` (TARGET) |
| `GET` | `/actuator/health` | Public (Liveness/Readiness only) | `-` | `{ status: "UP" }` | `200`, `503` | `< 25ms` (TARGET) |

## Database Design (`V15__create_audit_logs_table.sql`)

Single logical change in `V15` following [`V14__create_share_links_table.sql`](file:///d:/Upcraft/workspace/Upcraft-PG/src/main/resources/db/migration/V14__create_share_links_table.sql) and `AuditableEntity`:

```sql
CREATE TABLE IF NOT EXISTS audit_logs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    organization_id BINARY(16) NULL,
    property_id BINARY(16) NULL,
    actor_user_id BINARY(16) NULL,
    actor_email VARCHAR(255) NULL,
    actor_role VARCHAR(40) NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BINARY(16) NULL,
    summary VARCHAR(500) NOT NULL,
    metadata_json TEXT NULL,
    client_ip VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
    updated_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by VARCHAR(100) NULL,
    deleted_at DATETIME(6) NULL,
    deleted_by VARCHAR(100) NULL,
    status SMALLINT NOT NULL DEFAULT 1,
    audit_tracker_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_audit_logs_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_audit_logs_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    INDEX idx_audit_logs_org_created (organization_id, created_at),
    INDEX idx_audit_logs_prop_created (property_id, created_at),
    INDEX idx_audit_logs_action_created (action, created_at),
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_actor_created (actor_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- **Index Purpose:**
  - `idx_audit_logs_org_created` serves Owner paginated audit queries.
  - `idx_audit_logs_prop_created` serves Manager property-scoped audit queries.
  - `idx_audit_logs_action_created`, `idx_audit_logs_entity`, `idx_audit_logs_actor_created` serve filtered audit lookups.

## Performance & Complexity Analysis

| Endpoint | DB Queries | Time Complexity | Index Used | Max Payload | Target p95 | Risk |
|---|---|---|---|---|---|---|
| `GET /api/v1/search` | 1 user/scope check + 5 bounded `LIMIT 5` JPQL queries | `O(k log N)` bounded `k=5` | `idx_tenants_org_prop_status`, `uk_rooms_property_number_active`, `idx_beds_property_status`, `idx_invoices_prop_period_status`, `idx_payments_prop_paid_at`, `idx_complaints_org_prop_status` | <= 25 items (~8 KB) | `< 120ms` (TARGET) | `LIKE '%term%'` on large tenant table -> mitigated via `LIMIT 5` + property filter + min 2 chars |
| `GET /api/v1/audit-logs` | 1 user/scope check + 1 paged `SELECT` + 1 `COUNT` | `O(pageSize)` (`<=100`) | `idx_audit_logs_org_created` / `idx_audit_logs_prop_created` | 100 items (~35 KB) | `< 100ms` (TARGET) | Long history scan -> date range + index sort |
| Audited Write Mutations | +1 `INSERT` into `audit_logs` | `O(1)` | PK UUID + B-tree append | `-` | `+2ms` overhead | Transaction failure -> rolls back cleanly with write |

## Scalability & Load Plan

- **Little's Law Estimates (TARGET / ASSUMPTION, not measured):**
  - Average (`20 RPS`, `0.05s` avg latency): `L = 20 * 0.05 = 1` concurrent DB/worker slot.
  - Peak (`200 RPS`, `0.08s` avg latency): `L = 200 * 0.08 = 16` concurrent slots (fits default Tomcat `200` threads + HikariCP default `10`–`20` pool).
  - Burst (`100x` / `1M` spike): Caffeine `RateLimitFilter` (`max 10,000` IP buckets) rejects excess auth/share/upload/export traffic cleanly with `429 Too Many Requests` (`O(1)` in-memory atomic increment, zero DB hits for throttled traffic).
- **Protections:**
  - Hard pagination cap (`size <= 100`).
  - Search minimum query length (`2` chars), hard `PageRequest.of(0, 5)` per category.
  - Bounded Caffeine caches (`maximumSize=10000` rate limit, `500` dashboard cache).
  - Graceful `429` / `503` degradation, externalized HTTP provider timeouts (`5000ms`), transaction boundaries.
- **Load Verification Plan (Ask First before running load tool):**
  - `Upcraft-PG/scripts/load-test.js` (k6 script testing `/api/v1/search`, `/api/v1/audit-logs`, `/api/v1/dashboard/owner`, and auth `429` throttling at `20` & `200 RPS`; report actual measured p50/p95/error% separately).

## Security & Risks

- **Spoofed `X-Forwarded-For` Bypass:** Mitigated by strict `TrustedProxyResolver` checking `remoteAddr` against `app.rate-limit.trusted-proxies` before reading `X-Forwarded-For` / `X-Real-IP`.
- **PII / Secret Exposure in Audit Logs:** Mitigated via strict allowlist metadata builder rejecting `password`, `token`, `otp`, `secret`, `authorization`, `bankAccount`, etc.
- **Unauthorized Cross-Tenant/Property Search:** Mitigated by delegating scope resolution exclusively to `AccessGuardService` and adding dedicated resident predicates (`t.user.id = :userId OR ...`) for `PG_MEMBER`.
- **Public MySQL Exposure in Docker:** Mitigated by keeping `mysql` strictly on internal Docker bridge network without host `ports:` binding and supplying credentials only via `.env` / environment variables.

## Test & Load Verification

- **Unit Tests:**
  - `RateLimitAndMigrationSafetyTest` expanded for tiered buckets, trusted vs untrusted `X-Forwarded-For` spoofing rejection, and public share throttling.
  - `AuditAndSearchUnitTest`: metadata secret stripping, search query bounds (`< 2` chars rejected cleanly), immutability guarantees.
- **Integration Tests:**
  - `Milestone7AcceptanceSuiteIntegrationTest`: executes all **20 Acceptance Scenarios** end-to-end against MockMvc + Spring Security + H2 (`1..18` API workflows + search/audit/rate-limit failure & success paths + query-count assertions via Hibernate `Statistics`).
  - Update `FlywayMySqlIntegrationTest` to assert **15 migrations** (`V1`–`V15`) cleanly on both fresh schema and baselined legacy schema.
- **Backup & Restore Verification:**
  - Documented & tested locally against isolated Docker/MySQL scratch schema (never touching `pg_management` production rows).

## Rollout & Rollback

- **Rollout Order:** Apply `V15__create_audit_logs_table.sql` (additive only, zero breaking changes to existing tables) -> deploy Backend -> deploy Web -> release Mobile -> verify `/actuator/health` & `/api/v1/search`.
- **Rollback:** Revert app image to M6 build; `audit_logs` table can remain inert safely (`status <> 9` backward compatible, never drop live customer tables).

## Open Questions

None (Option A + all 7 modifications approved).

## Implementation Checklist

- [x] Add `spring-boot-starter-actuator` to `pom.xml` + `/actuator/health` permits in `SecurityConfig` & `application.properties`
- [x] Add `V15__create_audit_logs_table.sql` + SQL docs + update `FlywayMySqlIntegrationTest` (`V1`–`V15`)
- [x] Implement `AuditLogEntity`, `AuditLogRepository`, `AuditLogServiceImpl`, `AuditSanitizer`, `AuditLogV1Controller` + wire transactional audit calls across M1–M6 mutating services
- [x] Implement `GlobalSearchService`, `GlobalSearchServiceImpl`, `GlobalSearchV1Controller` (`GET /api/v1/search?q=`, role/org/property/tenant scoped, max 5/category)
- [x] Harden `RateLimitFilter` with trusted proxy CIDR verification & tiered limits (`auth`, `publicShare`, `uploads`, `sensitive`)
- [x] Add Backend/Web `Dockerfile`, root `docker-compose.yml`, `.env.docker.example`, backup/restore scripts & README docs
- [x] Write `AuditAndSearchUnitTest`, update `RateLimitAndMigrationSafetyTest`, and implement `Milestone7AcceptanceSuiteIntegrationTest` (all 20 scenarios + failure paths + query-count assertions) + k6 load script

## Change History

| Date | Change Description | Old Requirement (Commented) |
|---|---|---|
| 12-11-2026 | Initial Milestone 7 plan created (Option A + user hardening/docker/20-test modifications) | - |

