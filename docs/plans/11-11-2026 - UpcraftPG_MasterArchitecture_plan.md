# Upcraft PG — Master Architecture & Multi-Milestone Plan

## Status

Done (Milestones M1–M7 completed & verified)

## Baseline Git Checkpoints (Preserved)

- **Backend (`Upcraft-PG`):** branch `feat/development`, commit `481b0e8a18f42dfd4230b41395a5141eb4e795d2`
- **Web Frontend (`Upcraft-Shree-krishna-pg-frontend`):** branch `main` (Lovable-synced), commit `84dedf0b8565c9ab93841107d88df0d3ae945203`
- **Mobile (`Upcraft-PG-mobile`):** new Expo TypeScript repository created in Milestone 1

## Platform Architecture

- **Backend:** `Upcraft-PG` (Java 21, Spring Boot 4.1.0, MySQL 8+, Flyway, Spring Security + JWT, MapStruct, Caffeine, OpenAPI)
- **Web:** `Upcraft-Shree-krishna-pg-frontend` (React 19, TanStack Start/Router, Vite 8, Tailwind 4, Radix UI, Recharts)
- **Mobile:** `Upcraft-PG-mobile` (React Native, Expo, TypeScript, Expo Router, TanStack Query, RHF + Zod, Expo SecureStore, Expo Notifications)

## Key Architectural & Safety Corrections

1. **Flyway Baseline & Dual-Path Migration:**
   - Existing MySQL database uses Hibernate `UUID` (`BINARY(16)`). All new foreign keys referencing `users.id`, `rooms.id`, etc. use `BINARY(16)`.
   - `spring.flyway.baseline-on-migrate=true`, `baseline-version=1`.
   - `V1__baseline_existing_schema.sql` uses `CREATE TABLE IF NOT EXISTS` for fresh DB bootstrap; existing DBs baseline at `V1` without dropping or modifying existing rows.
   - `spring.jpa.hibernate.ddl-auto` switches from `update` to `validate`.
2. **MySQL Active-Record Uniqueness (Safeguard #4):**
   - Nullable `deleted_at` inside a composite `UNIQUE` index does NOT prevent duplicate active rows in MySQL (`NULL != NULL`).
   - Use a stored generated column (`active_guard TINYINT GENERATED ALWAYS AS (IF(status <> 9, 1, NULL)) STORED`) in `UNIQUE(property_id, number, active_guard)` for rooms/meters and `active_bed_guard BINARY(16) GENERATED ALWAYS AS (IF(tenancy_status = 'ACTIVE' AND status = 1, bed_id, NULL)) STORED` for tenancies.
3. **Money & Timezone:**
   - Replace `Double` price fields with `DECIMAL(12,2)` (`BigDecimal`).
   - Persist timestamps in UTC (`Instant` / `DATETIME(6)`), compute Indian billing dates in `Asia/Kolkata`.
4. **Security & Lovable Sync:**
   - Never log passwords, JWT secrets, OTPs, or PII.
   - Never alter Lovable remote/git history or force-push.
   - Enforce organization/property/tenant authorization exclusively on backend via `@accessGuard`.

## Milestone Summary

- **M1:** Foundation, Flyway Baseline (`V1`-`V4`), Audit Base, `MANAGER` Role, Org/Property/Invite Auth, Web Manager Shell, Expo Mobile App Shell
- **M2:** Multi-Property, Buildings/Floors/Rooms/Beds, Active-Bed Uniqueness, Vacancy, Move-In/Out Wizards
- **M3:** Rent Rules, Idempotent Invoices, Partial Payments, Ledger/Reversals, Security Deposits & Settlement
- **M4:** Electricity Meters, Photo Readings, Tariff Snapshots, Shared Splits
- **M5:** Complaints Upgrade, Announcements/Food Menu Alignment, Private Documents, Push & Provider Notifications
- **M6:** Real DB Dashboards, Reports, CSV/PDF Exports, Secure WhatsApp Share
- **M7:** Global Search, Audit Logs, Rate Limiting, Docker Compose, 20 Automated Acceptance Tests

