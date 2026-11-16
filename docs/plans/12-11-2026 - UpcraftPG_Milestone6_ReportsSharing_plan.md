# Upcraft PG Backend — Milestone 6: Real DB Dashboards, Reports, CSV/PDF Exports & Secure WhatsApp Sharing Plan

## Status

Done

## Module Overview

Implements Milestone 6 live database analytics, filtered reporting, streaming CSV/PDF document exports, and secure expiring share links for WhatsApp/native sharing across `Upcraft-PG`, `Upcraft-Shree-krishna-pg-frontend`, and `Upcraft-PG-mobile` using **Option A**:
- **Real DB Dashboards**:
  - `GET /api/v1/dashboard/owner` & `GET /api/v1/dashboard/manager` (filtered by `propertyId`, `year`, `month`): KPI cards (`totalProperties`, `totalRooms`, `totalBeds`, `occupiedBeds`, `vacantBeds`, `occupancyPercent`, `rentCollectedMonth`, `rentOutstanding`, `electricityDue`, `depositsHeld`, `openComplaints`, `pendingReadings`), charts (`monthlyCollectionVsOutstanding` 6-month series, `propertyOccupancyBreakdown`, `paymentStatusBreakdown`, `electricityConsumptionSeries`), and actionable lists (`upcomingDues`, `overdueTenants`, `recentPayments`, `recentMoveIns`, `recentMoveOuts`, `openComplaintsList`, `readingsRequiringReview`). Strictly separates `depositsHeld` from rent revenue and excludes `CANCELLED`/`WAIVED` invoices from outstanding totals.
  - `GET /api/v1/dashboard/tenant`: active tenancy summary, unpaid/overdue rent & electricity dues, recent payments, open complaints count, unread notices & unread notifications count, today's meals.
- **Reports & Exports**:
  - `GET /api/v1/reports/{reportType}` (`RENT_COLLECTION`, `OUTSTANDING`, `PAYMENTS`, `DEPOSITS`, `ELECTRICITY`, `OCCUPANCY`, `TENANTS`, `MOVE_IN_OUT`, `COMPLAINTS`) with property, status, and date-range filters, paginated (`size <= 100`) JSON summary + rows + totals.
  - `GET /api/v1/reports/{reportType}/export?format=csv|pdf` streaming CSV (`text/csv`) or OpenPDF (`application/pdf`) capped at `app.reports.max-export-rows=2000` with organization/property header, `Asia/Kolkata` timestamp, generated-by actor, and totals.
  - Individual PDF endpoints: `GET /api/v1/invoices/{invoiceId}/pdf`, `GET /api/v1/payments/{paymentId}/receipt-pdf`, `GET /api/v1/tenancies/{tenancyId}/deposit-statement-pdf`.
- **Secure Expiring Share Links & WhatsApp Share**:
  - Flyway `V14__create_share_links_table.sql`: `share_links` (`id`, `organization_id`, `property_id`, `created_by_user_id`, `token_hash` SHA-256 unique, `resource_type`, `resource_id`, `expires_at`, `revoked_at`, `view_count`, 8 audit columns + `version`).
  - Authenticated `POST /api/v1/share-links`, `GET /api/v1/share-links`, `DELETE /api/v1/share-links/{id}` returning raw URL-safe token once + WhatsApp share text (`wa.me`).
  - Public `GET /api/v1/public/share/{token}` and `GET /api/v1/public/share/{token}/pdf` verifying hash, expiry, and revocation; `PUBLIC_VACANCY` links expose ONLY property/room/bed vacancy counts & rent (zero tenant PII).

Out of scope: live WhatsApp Business Cloud API delivery (handled via Web Share API / `wa.me` deep link + expiring token URL per Master Prompt).

## Tech Stack

- **Backend**: Java 21, Spring Boot 4.1.0, MySQL 8+, Flyway (`V14`), Spring Data JPA JPQL aggregates, OpenPDF (`com.github.librepdf:openpdf`), Caffeine short-TTL cache, Jakarta Validation, `BigDecimal` (`DECIMAL(12,2)`).
- **Web**: React 19, TypeScript, TanStack Start/Router, TanStack Query, Recharts, Tailwind 4, Web Share API / `wa.me` share modal, blob download helper.
- **Mobile**: React Native, Expo SDK 58, TypeScript, Expo Router, TanStack Query, RN `Share.share` + `Linking.openURL("https://wa.me/...")`.

## Requirements

### Version 1 - [Date: 12-11-2026]

1. Owner/Manager dashboards return real DB metrics filtered by `propertyId` and `year`/`month`, respecting manager property assignments (`AccessGuardService`) and separating deposits from rent revenue.
2. Tenant dashboard returns strictly the authenticated resident's own tenancy, dues, complaints, notices, and notifications.
3. All 9 report types support `propertyId`, `fromDate`, `toDate`, pagination, and accurate totals reconciling with `invoices`, `payments`, `deposit_transactions`, `electricity_bills`, `beds`, `tenancies`, and `complaints`.
4. CSV and OpenPDF exports include organization/property metadata, `Asia/Kolkata` generation time, generated-by user name, itemized rows, and totals; individual invoice, payment receipt, and deposit settlement PDFs are downloadable and shareable.
5. Share links hash tokens with `HashUtils.sha256Hex`, enforce `expiresAt` (`72h` default, configurable max `168h`) and `revokedAt`, increment `viewCount`, and never expose tenant PII on `PUBLIC_VACANCY` share links.

## Non-Functional Requirements

- **Expected Scale (ASSUMPTION)**: 50–500 concurrent users, up to ~200 properties, ~100k invoices/payments over 1–2 years; average 5–15 req/s, peak 40 req/s.
- **Latency Targets (estimate)**:
  - `GET /api/v1/dashboard/{owner,manager,tenant}`: `p95 <= 250 ms` (`<= 5` aggregate/indexed queries + 30s Caffeine cache).
  - `GET /api/v1/reports/{type}` (paginated JSON): `p95 <= 250 ms`.
  - `GET /api/v1/reports/{type}/export` & PDF endpoints: `p95 <= 800 ms` (capped `<= 2000` rows, streamed).
  - `GET /api/v1/public/share/{token}`: `p95 <= 150 ms` (`O(1)` unique `token_hash` index lookup).

## Options Considered

| Option | Pattern | DB Queries / Path | 10x / 100x Load | Effort | Fit | Main Risk |
|---|---|---|---|---|---|---|
| **Option A (Chosen)** | JPQL Aggregates + Streaming OpenPDF + `share_links` | 3–5 aggregates (`O(1)` grouped) | Streamed `O(1)` heap + Caffeine | **M** | High | `openpdf` Maven download |
| Option B | Materialized Snapshots + Stateless Token | 1–2 snapshot reads | Fast reads, stale window | L | Low | Stale totals / unrevocable links |
| Option C | In-Memory + Client JS PDF/CSV | `O(n)` entity scans | Heap spike on concurrent exports | M | Low | OOM + Web/Mobile drift |

**Recommendation:** Option A — real-time financial reconciliation with `invoices`/`payments`/`deposits`, consistent server-side OpenPDF documents across Web/Mobile/WhatsApp share, and revocable `share_links`.

## Architecture & Design

- `pom.xml`: add `com.github.librepdf:openpdf:2.0.3` (or compatible stable 2.0.x).
- `V14__create_share_links_table.sql`: creates `share_links` + composite index `idx_share_links_org_created` and `UNIQUE KEY uk_share_links_token_hash (token_hash)`.
- `com.upcraft.stay.reporting.*`:
  - `enums/`: `ReportType`, `ExportFormat`, `ShareResourceType`.
  - `entity/`: `ShareLinkEntity` (extends `AuditableEntity`).
  - `repository/`: `ShareLinkRepository` + JPQL aggregate/projection methods in `InvoiceRepository`, `PaymentRepository`, `DepositTransactionRepository`, `ElectricityBillRepository`, `BedRepository`, `TenancyRepository`, `ComplaintRepository`, `MeterReadingRepository`.
  - `dto/`: `StaffDashboardResponseDTO`, `TenantDashboardResponseDTO`, `ReportResponseDTO<T>`, `ShareLinkResponseDTO`, `CreateShareLinkRequestDTO`, `PublicShareViewResponseDTO`.
  - `service/` + `impl/`: `DashboardV1Service`/`Impl`, `ReportV1Service`/`Impl`, `PdfExportService`/`Impl`, `CsvExportService`/`Impl`, `ShareLinkV1Service`/`Impl`.
  - `controller/`: `DashboardV1Controller`, `ReportV1Controller`, `ShareLinkV1Controller`, `PublicShareV1Controller` (permitAll on `/api/v1/public/share/**` in `SecurityConfig`).

## API Endpoints

| Method & Path | Roles / Guard | Request | Response / Status | Target p95 |
|---|---|---|---|---|
| `GET /api/v1/dashboard/owner` | `PG_OWNER` | `propertyId`, `year`, `month` | `200 OK` `StaffDashboardResponseDTO` | `<= 250ms` |
| `GET /api/v1/dashboard/manager` | `MANAGER` | `propertyId`, `year`, `month` | `200 OK` `StaffDashboardResponseDTO` | `<= 250ms` |
| `GET /api/v1/dashboard/tenant` | `PG_MEMBER` | — | `200 OK` `TenantDashboardResponseDTO` | `<= 200ms` |
| `GET /api/v1/reports/{reportType}` | `PG_OWNER` / `MANAGER` | `propertyId`, `fromDate`, `toDate`, `page`, `size` | `200 OK` `ReportResponseDTO` | `<= 250ms` |
| `GET /api/v1/reports/{reportType}/export` | `PG_OWNER` / `MANAGER` | `format=CSV\|PDF`, `propertyId`, `fromDate`, `toDate` | `200 OK` binary stream | `<= 800ms` |
| `GET /api/v1/invoices/{id}/pdf` | Owner / Manager / owning Member | — | `200 OK` `application/pdf` | `<= 500ms` |
| `GET /api/v1/payments/{id}/receipt-pdf` | Owner / Manager / owning Member | — | `200 OK` `application/pdf` | `<= 500ms` |
| `GET /api/v1/tenancies/{id}/deposit-statement-pdf` | Owner / Manager / owning Member | — | `200 OK` `application/pdf` | `<= 500ms` |
| `POST /api/v1/share-links` | `PG_OWNER` / `MANAGER` / `PG_MEMBER` (own resources only) | `CreateShareLinkRequestDTO` | `201 Created` `ShareLinkResponseDTO` | `<= 200ms` |
| `GET /api/v1/share-links` | `PG_OWNER` / `MANAGER` | `page`, `size` | `200 OK` `Page<ShareLinkResponseDTO>` | `<= 200ms` |
| `DELETE /api/v1/share-links/{id}` | Owner / Manager / creator | — | `204 No Content` | `<= 150ms` |
| `GET /api/v1/public/share/{token}` | Public (`permitAll`) | — | `200 OK` `PublicShareViewResponseDTO` | `<= 150ms` |
| `GET /api/v1/public/share/{token}/pdf` | Public (`permitAll`) | — | `200 OK` `application/pdf` | `<= 500ms` |

## Database Design

- **`V14__create_share_links_table.sql`**:
  - `share_links`: `id BINARY(16) PK`, `organization_id BINARY(16) NOT NULL`, `property_id BINARY(16) NULL`, `created_by_user_id BINARY(16) NOT NULL`, `token_hash VARCHAR(64) NOT NULL`, `resource_type VARCHAR(30) NOT NULL`, `resource_id BINARY(16) NOT NULL`, `expires_at DATETIME(6) NOT NULL`, `revoked_at DATETIME(6) NULL`, `view_count BIGINT NOT NULL DEFAULT 0`, plus 8 mandatory audit columns and `version BIGINT NOT NULL DEFAULT 0`.
  - `UNIQUE KEY uk_share_links_token_hash (token_hash)`, `INDEX idx_share_links_org_created (organization_id, created_at)`.

## Performance & Complexity Analysis

| Endpoint | DB Queries | Time Complexity | Index Used | Max Payload | Target p95 | Risk |
|---|---|---|---|---|---|---|
| `GET /api/v1/dashboard/{owner,manager}` | 4–5 aggregate/top-N queries | `O(1)` grouped + `O(6)` chart months | `idx_invoices_prop_period`, `idx_beds_prop` | ~45 KB | `<= 250ms` | Slow queries on 100k+ rows -> aggregate queries + 30s Caffeine cache |
| `GET /api/v1/reports/{type}` | 2–3 (count + paged rows + totals) | `O(k)` (`k <= 100`) | `idx_invoices_prop_period`, `idx_deposit_tx_prop` | ~100 KB | `<= 250ms` | Large date range -> bounded page size (`<= 100`) |
| `GET /api/v1/reports/{type}/export` | 1 bounded query (`<= 2000` rows) | `O(r)` streamed | Same as above | ~1.5 MB | `<= 800ms` | Huge org export -> cap `2000` rows + streaming writer |
| `GET /api/v1/public/share/{token}` | 2 (lookup by hash + target entity) | `O(1)` | `uk_share_links_token_hash` | ~25 KB | `<= 150ms` | Token brute-force -> 256-bit random token + `RateLimitFilter` |

## Scalability & Load Plan

- **Little's Law**:
  - Expected (15 req/s, avg 50 ms): `L = 0.75` concurrent requests (well within HikariCP 10–20 conns).
  - 10x peak (150 req/s, avg 80 ms): `L = 12` concurrent requests; 30s Caffeine cache absorbs repeated dashboard reads (`>= 80%` cache hit estimate); streaming PDF/CSV keeps per-request heap `< 2 MB`.
  - 1M burst: `RateLimitFilter` returns `429`, bounded export cap (`2000` rows) prevents heap exhaustion, HikariCP `connection-timeout` fails fast with `503`.
- **Memory**: `ByteArrayOutputStream` / direct `OutputStream` for single PDFs (`< 500 KB`), row cap `2000` for tabular exports, Caffeine `maximumSize=500`, `expireAfterWrite=30s`.

## Security & Risks

- **Zero PII on Public Vacancy Links**: `PUBLIC_VACANCY` share links only query `properties`, `rooms`, and `beds` (`VACANT` status) — never `tenants`, `tenancies`, `invoices`, or `users`.
- **Scoped Capability Tokens**: Raw 256-bit tokens returned once on creation, stored as SHA-256 hex (`HashUtils.sha256Hex`), always validated for `expiresAt > now` and `revokedAt == null`.
- **Cross-Tenant / Cross-Property Guards**: Residents cannot generate `PUBLIC_VACANCY` or other tenants' invoice/receipt/deposit share links or PDFs (`403 Forbidden`); managers restricted to assigned properties.

## Test & Load Verification

- Unit tests: CSV/OpenPDF byte generation (`%PDF-`), share-link expiry & revocation checks, dashboard deposits-not-revenue reconciliation math.
- Integration tests (`MockMvc` + H2): Owner/Manager/Tenant dashboards reconcile with real invoices/payments/deposits/electricity/complaints, all 9 report JSON + CSV + PDF exports return `200`, individual invoice/receipt/deposit PDFs return `application/pdf`, `PUBLIC_VACANCY` share link has zero tenant PII, expired/revoked share tokens return `404`/`410`, cross-org access rejected (`403`).
- `FlywayMySqlIntegrationTest` updated to expect `V1`–`V14`.

## Rollout & Rollback

- **Rollout**: Add `openpdf` dependency, apply `V14`, deploy backend, Web, and Mobile.
- **Rollback**: Revert jar to M5; `V14` only adds `share_links` table.

## Open Questions

- None blocking (`openpdf` addition to `pom.xml` requires Maven download approval on build).

## Implementation Checklist

- [x] Add `com.github.librepdf:openpdf` to `pom.xml` + `app.reports.*` & `app.share.*` config in `AppProperties` / `application.properties`
- [x] Flyway `V14__create_share_links_table.sql` + SQL verification doc + `FlywayMySqlIntegrationTest` update
- [x] Aggregate JPQL queries on existing repositories + `ShareLinkEntity` / `ShareLinkRepository`
- [x] `DashboardV1Service`, `ReportV1Service`, `PdfExportService`, `CsvExportService`, `ShareLinkV1Service` & controllers + `SecurityConfig` `permitAll` for `/api/v1/public/share/**`
- [x] Unit + `Milestone6ReportsSharingIntegrationTest` passing
- [x] Web (`Upcraft-Shree-krishna-pg-frontend`) real DB Owner/Manager/Member dashboards, Reports & Exports page, Share modal & public share route `/share/$token`
- [x] Mobile (`Upcraft-PG-mobile`) real DB Owner/Manager/Tenant dashboards, Reports & WhatsApp Share integration + Vitest tests
- [x] Full backend/web/mobile build, typecheck & test verification

## Change History

| Date | Change Description | Old Requirement (Commented) |
|---|---|---|
| 12-11-2026 | Initial Milestone 6 plan created (Option A) | - |

