# Upcraft PG Backend — Milestone 4: Electricity (Meters, Photo Readings, Tariff Snapshots, Split Billing & Reports) Plan

## Status

Done (verified: 26 passed / 0 failed / 3 MySQL-env skipped in Backend `mvn test`; Web `vite build` + `tsc --noEmit` + `eslint` exit 0; Mobile `tsc --noEmit` + 15/15 Vitest + `expo export` exit 0)

## Module Overview

Implements Milestone 4 electricity metering and private file-storage workflows across `Upcraft-PG`, `Upcraft-Shree-krishna-pg-frontend`, and `Upcraft-PG-mobile`:
- Property/room/bed-scoped electricity meters (`BED`, `ROOM`, `SHARED`, `MAIN`) with configurable tariff rates, fixed monthly charges, and split strategies (`SINGLE_TENANT`, `EQUAL`, `PER_BED`, `PERCENTAGE`, `MANUAL`).
- Private file storage boundary (`stored_files`, `FileStorageBoundary`, `LocalPrivateFileStorageAdapter`) with MIME allowlist, size limits, checksum recording, path traversal protection, and authorized-only download streaming (`/api/v1/files/{fileId}/content`).
- Meter reading capture (`previousValue`, `currentValue`, `unitsConsumed`), reading photo attachment, automatic unusual-usage detection, lower-reading rejection unless authorized meter reset/correction workflow with mandatory reason, and DB-enforced single active reading per `(meter, billingYear, billingMonth)`.
- Immutable tariff and reading snapshots persisted on both `meter_readings` and `electricity_bills` so historical electricity charges never change when tariffs are updated later.
- Shared meter split engine generating itemized `ElectricityBillEntity` shares and linked `ELECTRICITY` invoices (`invoices` + `invoice_items`), automatically reconciling into Tenant Bills, Tenant Ledger, Partial Payments, and Move-Out settlements.
- Property-scoped Consumption Report & resident-isolated `/api/v1/electricity/bills/mine`.

## Tech Stack

- Backend: Java 21, Spring Boot 4.1.0, MySQL 8+, Flyway (`V11`), Spring Data JPA (`@EntityGraph`), Jakarta Validation, `BigDecimal` (`DECIMAL(12,2)` / `DECIMAL(10,4)`), SLF4J, local private filesystem storage adapter.
- Web: React 19, TypeScript, TanStack Start/Router, Tailwind 4, Radix UI, TanStack Query, Zod, Recharts.
- Mobile: React Native, Expo SDK 58, TypeScript, Expo Router, TanStack Query, RHF + Zod.

## Requirements

### Version 1 — [Date: 12-11-2026]

- CRUD/lifecycle for electricity meters (`POST /api/v1/meters`, `PUT /api/v1/meters/{id}`, `GET /api/v1/meters`).
- Private authenticated upload and download for meter photos (`POST /api/v1/files/upload`, `GET /api/v1/files/{id}/content`).
- Submit meter readings with automatic `units = current - previous`, `energyCharge = units * rateSnapshot`, `totalAmount = energy + fixed + additional + tax - credit`.
- Block duplicate readings per `(meter, billingYear, billingMonth)` while allowing rejected resubmissions or authorized correction/reset entries.
- Reject `currentValue < previousValue` unless explicitly submitted with `allowResetOrCorrection = true`, mandatory reason, and required authorization.
- Automatically flag unusual consumption against configurable historical baseline/threshold.
- Approve/reject readings and split approved readings across active tenancies using `SINGLE_TENANT`, `EQUAL`, `PER_BED`, `PERCENTAGE`, or `MANUAL` allocation, generating linked `ELECTRICITY` invoices cleanly.
- Ensure tariff updates on meters or properties never retroactively alter previously recorded readings or generated bills.

## Non-Functional Requirements

- **ASSUMPTION (Load & Scale):** 50–500 concurrent users, up to ~200 properties / ~3,000 meters / ~36,000 readings/year.
- **Latency Targets (estimate):**
  - Paginated meters/readings/bills queries (`<= 3` queries via `@EntityGraph`, indexed FKs): target p95 `<= 200 ms`.
  - Split generation & linked invoice creation: single ACID `@Transactional` block (`p95 <= 350 ms`).
  - Photo upload/stream (`<= 5 MB` validated binary): `p95 <= 500 ms`.
- **Precision & Timezone:** `unitRate` stored as `DECIMAL(10,4)`, money amounts as `DECIMAL(12,2)`, dates evaluated in `Asia/Kolkata`, UTC audit timestamps.
- **Security:** `METERS` permission enforced for manager meter/reading actions; private file downloads verify org/property/tenant authorization before streaming bytes.

## Options Considered

| Option | Approach | Verdict |
|---|---|---|
| Option A | `stored_files` + `meters` + `meter_readings` + `electricity_bills` (linked to `invoices`) with immutable snapshots & private download endpoint | **Chosen** — full M3 payment/ledger reconciliation, private photo security, immutable historical billing |
| Option B | Public `/uploads/**` static URLs + inline JSON bills without invoices | **Rejected** — violates private storage requirement and splits tenant payments from M3 receipts/ledgers |
| Option C | Dynamic tariff recalculation on read | **Rejected** — violates immutable historical bill requirement when rates change |

## API Endpoints

| Method & Path | Roles / Guard | Request DTO | Response / Status |
|---|---|---|---|
| `POST /api/v1/files/upload` | Owner / Manager / permitted Member | Multipart (`file`, `propertyId`, `category`) | `201 Created` (`StoredFileResponseDTO`) |
| `GET /api/v1/files/{fileId}/content` | Owner / Manager / owning Resident | — | `200 OK` binary stream |
| `GET /api/v1/meters` | Owner / assigned Manager | Filters (`propertyId`, `meterScope`, `meterStatus`, `search`) | `200 OK` paginated `MeterResponseDTO` |
| `POST /api/v1/meters` | Owner / Manager (`METERS`) | `CreateMeterRequestDTO` | `201 Created` (`MeterResponseDTO`) |
| `PUT /api/v1/meters/{meterId}` | Owner / Manager (`METERS`) | `UpdateMeterRequestDTO` | `200 OK` (`MeterResponseDTO`) |
| `GET /api/v1/meters/{meterId}/readings` | Owner / assigned Manager | Pagination | `200 OK` paginated `MeterReadingResponseDTO` |
| `GET /api/v1/meter-readings` | Owner / assigned Manager | Filters (`propertyId`, `meterId`, `status`, `year`, `month`, `unusualOnly`) | `200 OK` paginated `MeterReadingResponseDTO` |
| `POST /api/v1/meters/{meterId}/readings` | Owner / Manager (`METERS`) | `RecordMeterReadingRequestDTO` | `201 Created` (`MeterReadingResponseDTO`) |
| `POST /api/v1/meter-readings/{readingId}/approve` | Owner / Manager (`METERS`) | `ApproveReadingRequestDTO` | `200 OK` (`MeterReadingResponseDTO`) |
| `POST /api/v1/meter-readings/{readingId}/reject` | Owner / Manager (`METERS`) | `RejectReadingRequestDTO` | `200 OK` (`MeterReadingResponseDTO`) |
| `POST /api/v1/meter-readings/{readingId}/generate-bills` | Owner / Manager (`METERS`/`PAYMENTS`) | `GenerateElectricityBillsRequestDTO` | `201 Created` (`List<ElectricityBillResponseDTO>`) |
| `GET /api/v1/electricity/bills` | Owner / assigned Manager | Filters (`propertyId`, `year`, `month`, `tenantId`, `meterId`) | `200 OK` paginated `ElectricityBillResponseDTO` |
| `GET /api/v1/electricity/bills/mine` | `PG_MEMBER` | Pagination | `200 OK` paginated `ElectricityBillResponseDTO` |
| `GET /api/v1/electricity/consumption-report` | Owner / assigned Manager | Filters (`propertyId`, `billingYear`, `billingMonth`) | `200 OK` (`ConsumptionReportResponseDTO`) |

## Database Design

- **`V11__create_storage_and_electricity_tables.sql`**: Creates `stored_files`, `meters`, `meter_readings`, and `electricity_bills` — all using `BINARY(16)` UUID keys, 8 mandatory audit columns, `DECIMAL(10,4)` / `DECIMAL(12,2)` precision, and MySQL generated-column uniqueness guards (`active_guard`, `active_period_guard`, `active_reading_tenancy_guard`).

## Implementation Checklist

- [x] Architecture & API plan created
- [x] SQL documentation & Flyway `V11` migration created
- [x] Private file storage service (`FileStorageBoundary`, `LocalPrivateFileStorageAdapter`, `FileV1Controller`) implemented
- [x] Electricity enums, entities, repositories, mapper, split calculation helper, services & `ElectricityV1Controller` implemented
- [x] Unit tests + `Milestone4ElectricityIntegrationTest` passing
- [x] Web frontend (`Upcraft-Shree-krishna-pg-frontend`) Owner, Manager & Member Electricity screens + authorized photo modal wired
- [x] Mobile app (`Upcraft-PG-mobile`) Owner, Manager & Tenant Electricity meters, readings, bills & consumption screens wired
- [x] Full backend/web/mobile build & test regression verified
