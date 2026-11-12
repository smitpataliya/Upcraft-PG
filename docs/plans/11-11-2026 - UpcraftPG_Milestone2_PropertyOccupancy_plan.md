# Upcraft PG Backend — Milestone 2: Property & Occupancy Plan

## Status

Done — verified across Backend (`17 passed, 0 failed, 3 skipped`), Web (`tsc`, `vite build`, `eslint`), and Mobile (`tsc`, `vitest 8/8`, `expo export`)

## Module Overview

Implements Milestone 2 across `Upcraft-PG`, `Upcraft-Shree-krishna-pg-frontend`, and `Upcraft-PG-mobile`:
- Property configuration updates, archive guard (blocked when active tenancies exist), and live database occupancy summary.
- Normalized hierarchy: `Organization > Property > Floor (optional) > Room > Bed`.
- Active-bed uniqueness at both service/transaction level (`PESSIMISTIC_WRITE`) and MySQL 8+ database constraint level (`active_bed_guard` generated column).
- Vacant-bed availability filtering across accessible properties without exposing tenant PII.
- Tenant profile directory, tenancy Move-In Wizard, Bed Transfer, Move-Out Settlement with deposit balance calculation, and Tenant self-residency view (`/api/v1/tenants/me`), while preserving legacy `/api/rooms/**` and `/api/members/**`.

## Tech Stack

- Backend: Java 21, Spring Boot 4.1.0, MySQL 8+, Flyway (`V5`–`V8`), Spring Data JPA, MapStruct, Jakarta Validation, Spring Security (`@accessGuard`).
- Web: React 19, TypeScript, TanStack Start/Router, Tailwind 4, Radix UI, TanStack Query, Zod.
- Mobile: React Native, Expo SDK 58, TypeScript, Expo Router, TanStack Query, RHF + Zod.

## Requirements

### Version 1 - [Date: 11-11-2026]

- Upgrade property lifecycle with `PUT /api/v1/properties/{propertyId}`, `PATCH /api/v1/properties/{propertyId}/archive` (rejecting archive when active/notice/reserved tenancies exist), and `GET /api/v1/properties/{propertyId}/summary`.
- Support optional floors per property (`FloorEntity`, unique `(property_id, floor_number)` and `(property_id, name)` among active rows).
- Upgrade legacy `rooms` safely (`V6`) with `property_id`, optional `floor_id`, `room_type`, `DECIMAL(12,2)` rent/deposit defaults, `room_status`, optimistic `@Version`, and `active_guard` unique constraint per property while keeping `/api/rooms/**` functional.
- Create normalized `beds` (`VACANT`, `OCCUPIED`, `RESERVED`, `MAINTENANCE`, `INACTIVE`) with `monthly_rent` and `default_deposit` (`DECIMAL(12,2)`).
- Create normalized `tenants` and `tenancies` with `active_bed_guard` ensuring at most one `ACTIVE` tenancy per bed.
- Build Move-In Wizard (allocate vacant bed, create/reuse tenant, set rent/deposit/dates/initial meter reading, sync bed & room occupancy, optionally trigger portal invitation), Bed Transfer, Bed Reserve, and Move-Out Settlement (final meter reading, unpaid dues, deductions with reason, itemized refundable deposit, close tenancy, release bed, preserve history).
- Provide paginated vacant-bed availability endpoint (`GET /api/v1/beds/availability`) filtered by property, room type, bed status, max rent, and available-before date.
- Provide `GET /api/v1/tenants/me` returning strictly the logged-in `PG_MEMBER` resident's active/past tenancy, bed, room, and property summary.

## Non-Functional Requirements

- **ASSUMPTION (Load & Scale):** 50–500 concurrent active users, up to ~200 properties / 10,000 beds initially.
- **Latency Targets (estimate before load test):**
  - Paginated read/filter endpoints (`/floors`, `/rooms`, `/beds/availability`, `/tenants`, `/tenancies`): `<= 4` DB queries (`COUNT` + page query + count/lookup projections, zero N+1), target p95 `<= 250 ms`.
  - Transactional write endpoints (`move-in`, `transfer`, `move-out`): pessimistic lock on target `beds` row (`<= 300 ms` p95) preventing race-condition double allocation.
- **Little's Law Capacity Check:** At peak `100 req/s` and `0.15 s` avg latency, `L = 15` concurrent requests (covered comfortably by Tomcat default thread pool + HikariCP connection pool); every list endpoint bounded via `PaginationUtils` (`defaultSize=10`, `maxSize=100`).
- **Security:** Owner has organization-wide access; `MANAGER` strictly scoped to assigned properties (`canManageTenants` required for tenant/move-in/move-out/transfer/reserve writes); `PG_MEMBER` limited to `/api/v1/tenants/me`.

## Options Considered

| Option | Approach | Verdict |
|---|---|---|
| Option A | Normalized `floors`, upgraded `rooms` (`property_id`, `floor_id`, `DECIMAL`), `beds`, `tenants`, `tenancies` + generated `active_bed_guard` + `@Lock(PESSIMISTIC_WRITE)` | **Chosen** — preserves legacy compatibility, eliminates double-allocation races at DB + JPA level, separates tenant PII from occupancy history |
| Option B | Store residency directly on `users` and `rooms.occupied` only | **Rejected** — fails move-in/move-out history, transfer history, bed-specific pricing, and race safety |
| Option C | Separate read-only reporting view + event-sourced occupancy | **Rejected** — over-engineered for current single-service monolith phase |

## API Endpoints & Contracts

All responses use `ApiResponseDTO<T>` with `X-Trace-Id`; lists use `PageResponseDTO<T>` with `defaultSize=10`, `maxSize=100`, whitelisted indexed `sortBy`.

| Method & Path | Roles / Guard | Request DTO | Response / Status |
|---|---|---|---|
| `PUT /api/v1/properties/{propertyId}` | `PG_OWNER` | `UpdatePropertyRequestDTO` | `200 OK` (`PropertySummaryResponseDTO`) |
| `PATCH /api/v1/properties/{propertyId}/archive` | `PG_OWNER` | — | `200 OK` (`PropertySummaryResponseDTO`), `409 Conflict` if active tenancies |
| `GET /api/v1/properties/{propertyId}/summary` | `PG_OWNER`, assigned `MANAGER` | — | `200 OK` (`PropertyOccupancySummaryResponseDTO`) |
| `GET/POST /api/v1/properties/{propertyId}/floors` | Owner / assigned Manager | `CreateFloorRequestDTO` | `200`/`201` paginated `FloorResponseDTO` |
| `PUT/DELETE /api/v1/properties/{propertyId}/floors/{floorId}` | Owner / assigned Manager (`ROOMS`/`TENANTS`) | `UpdateFloorRequestDTO` | `200 OK` / `204` (`409` if active rooms attached) |
| `GET/POST /api/v1/properties/{propertyId}/rooms` | Owner / assigned Manager | `CreatePropertyRoomRequestDTO` | `200`/`201` paginated `PropertyRoomResponseDTO` |
| `GET/PUT/DELETE /api/v1/properties/{propertyId}/rooms/{roomId}` | Owner / assigned Manager | `UpdatePropertyRoomRequestDTO` | `200`/`204` (`409` if occupied/active tenancies) |
| `GET/POST /api/v1/rooms/{roomId}/beds` | Owner / assigned Manager | `CreateBedRequestDTO` | `200`/`201` paginated `BedResponseDTO` |
| `PUT/PATCH /api/v1/beds/{bedId}` & `/beds/{bedId}/status` | Owner / assigned Manager | `UpdateBedRequestDTO` / `UpdateBedStatusRequestDTO` | `200 OK` (`BedResponseDTO`, `409` on unsafe occupied transition) |
| `GET /api/v1/beds/availability` | Owner / assigned Manager | Query filters (`propertyId`, `roomType`, `bedStatus`, `maxRent`, `availableBefore`) | `200 OK` paginated `BedAvailabilityResponseDTO` |
| `GET /api/v1/beds/{bedId}/history` | Owner / assigned Manager | Pagination params | `200 OK` paginated `TenancyResponseDTO` |
| `GET/POST /api/v1/tenants` | Owner / assigned Manager | `CreateTenantRequestDTO` | `200`/`201` paginated `TenantSummaryResponseDTO` |
| `GET/PUT /api/v1/tenants/{tenantId}` | Owner / assigned Manager | `UpdateTenantRequestDTO` | `200 OK` (`TenantDetailResponseDTO`) |
| `GET /api/v1/tenants/me` | `PG_MEMBER` | — | `200 OK` (`TenantSelfPortalResponseDTO`) |
| `GET /api/v1/tenancies` | Owner / assigned Manager | Filters (`propertyId`, `tenancyStatus`, `tenantId`) | `200 OK` paginated `TenancyResponseDTO` |
| `POST /api/v1/tenancies/move-in` | Owner / Manager (`TENANTS`) | `MoveInRequestDTO` | `201 Created` (`TenancyResponseDTO`, `409` if bed unavailable) |
| `POST /api/v1/tenancies/reserve` | Owner / Manager (`TENANTS`) | `ReserveBedRequestDTO` | `201 Created` (`BedResponseDTO`) |
| `POST /api/v1/tenancies/{tenancyId}/transfer` | Owner / Manager (`TENANTS`) | `TransferBedRequestDTO` | `200 OK` (`TenancyResponseDTO`) |
| `POST /api/v1/tenancies/{tenancyId}/move-out` | Owner / Manager (`TENANTS`) | `MoveOutRequestDTO` | `200 OK` (`TenancyResponseDTO`) |

## Database Design

- **`V5__create_floors_table.sql`**: `floors` table with 8 audit columns, `active_guard`, unique active floor number and name per property.
- **`V6__upgrade_rooms_and_create_beds_table.sql`**: Safely drops legacy single-column `rooms.number` unique index via `INFORMATION_SCHEMA.STATISTICS`, adds `property_id`, `floor_id`, `DECIMAL(12,2)` money fields, `active_guard` per-property uniqueness, and creates `beds` table.
- **`V7__create_tenants_and_tenancies_tables.sql`**: Creates `tenants` and `tenancies` tables with `active_bed_guard` preventing overlapping `ACTIVE` tenancies on any bed.
- **`V8__backfill_rooms_beds_tenancies.sql`**: Idempotently attaches legacy `rooms` to the earliest active property, creates bed inventory matching capacity/occupied counts, and creates `tenants`/`tenancies` for legacy assigned `PG_MEMBER` users.

## Implementation Checklist

- [x] Architecture & API plan created
- [x] SQL documentation & Flyway `V5`–`V8` migrations written
- [x] Domain enums, upgraded entities, new entities & repositories
- [x] Property update/archive/summary, Floor, Room, Bed, Tenant & Tenancy services + controllers
- [x] Unit tests + `Milestone2PropertyOccupancyIntegrationTest`
- [x] Web frontend (`Upcraft-Shree-krishna-pg-frontend`) multi-property Floors/Rooms/Beds, Vacancy, Tenants & Move-In/Out Wizards
- [x] Expo mobile (`Upcraft-PG-mobile`) Owner/Manager Occupancy, Vacancy, Tenants & Move-In/Out + Tenant `/tenants/me` residency view
- [x] Regression verification (`mvn test`, Web `tsc`/`build`/`eslint`, Mobile `tsc`/`vitest`/`export`) — MySQL `FlywayMySqlIntegrationTest` (3 tests) remains skipped pending `MYSQL_TEST_PASSWORD`

## Change History

| Date | Change Description | Old Requirement (Commented) |
| --- | --- | --- |
| 11-11-2026 | Initial Milestone 2 plan created | — |

