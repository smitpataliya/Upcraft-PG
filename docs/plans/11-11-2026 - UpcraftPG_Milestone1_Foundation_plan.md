# Upcraft PG — Milestone 1: Foundation, Flyway Baseline, Multi-Org Auth & Role Shells Plan

## Status

Code complete (pending live MySQL Flyway verification)

## Module Overview

Establishes production foundation across `Upcraft-PG`, `Upcraft-Shree-krishna-pg-frontend`, and new `Upcraft-PG-mobile`:
- Safe Flyway MySQL baseline (`V1`-`V4`) preserving all existing data and supporting fresh DB creation.
- Mandatory 8-column JPA audit base (`AuditableEntity`), `ApiResponseDTO<T>`, `PageResponseDTO<T>`, `ErrorCode`, `TraceIdFilter`, hardened `GlobalExceptionHandler`, and auth rate limiting.
- Multi-tenant Organization, Property scoping, `MANAGER` role, property assignment, and secure Invitation onboarding while preserving legacy `/api/auth/*`, `SUPER_ADMIN`, `PG_OWNER`, and `PG_MEMBER` logins.
- Web auth/session alignment + `/manager/*` shell + property switcher + `.env.example`.
- Native Android + iOS `Upcraft-PG-mobile` Expo application with role-based navigation and real backend integration.

## Tech Stack

- Backend: Java 21, Spring Boot 4.1.0, MySQL 8+, Flyway, Spring Security + JWT, MapStruct, Apache Commons Lang3/Collections4, Caffeine
- Web: React 19, TypeScript, TanStack Start/Router, Vite 8, Tailwind 4, Radix UI
- Mobile: React Native, Expo, TypeScript, Expo Router, TanStack Query, React Hook Form, Zod, Expo SecureStore, Expo Notifications

## Requirements

### Version 1 - [Date: 11-11-2026]

- Baseline existing MySQL 10 tables safely via Flyway `V1` without dropping/truncating/resetting data, and allow clean fresh DB migration.
- Add `AuditableEntity` (8 audit columns) + `@EnableJpaAuditing` + `AuditorAware` for all new entities; safely add missing audit columns to legacy tables in `V2` without colliding with `complaints.status` or `queries.status`.
- Add `Role.MANAGER`, `OrganizationEntity`, `OrganizationMemberEntity`, `PropertyEntity`, `ManagerPropertyAssignmentEntity`, `InvitationEntity`.
- Add `/api/v1/auth/**` (owner+org register, login, me, logout, forgot/verify-otp/reset), `/api/v1/organizations/**`, `/api/v1/invitations/**`, `/api/v1/properties` (paginated, role-scoped).
- Keep legacy `/api/auth/*` backward-compatible (`AuthResponse` includes both flat fields and `user` object).
- Remove startup `ALTER TABLE` from `DataSeeder`, restrict dev seeding to `dev`/`local` profile, never log passwords/OTPs/secrets.
- Fix `CustomUserDetailsService` to enforce `user.isActive()` and safe SSO null-password handling.
- Update `Upcraft-Shree-krishna-pg-frontend` with real auth, `MANAGER` shell (`/manager/*`), property switcher, and `VITE_USE_MOCK_API` control while preserving Lovable git history.
- Create `Upcraft-PG-mobile` native Expo app with role-based auth & shells (`PG_OWNER`, `MANAGER`, `PG_MEMBER`, `SUPER_ADMIN`), SecureStore token storage, skeleton loaders, and `expo-notifications` registration hook.

## Non-Functional Requirements

- **ASSUMPTION (Load & Scale):** Initial tier 50-500 concurrent users, max page size 100 (`app.pagination.max-size=100`), default 10.
- **ASSUMPTION (Latency Targets):** Auth/Read endpoints p95 <= 300 ms; Write/Register/Invite endpoints p95 <= 500 ms.
- **Security:** BCrypt password hashing, SHA-256 hashed invitation tokens/codes in DB, rate limit on `/api/v1/auth/**` & `/api/auth/**` (`429 Retry-After`), strict backend property/organization authorization (`@accessGuard`).

## Implementation Checklist

- [x] Backend dependencies, externalized config, `.env.example`, `.gitignore`
- [x] Flyway `V1`-`V4` migrations + SQL documentation file
- [x] `AuditableEntity`, `ApiResponseDTO`, `PageResponseDTO`, `ErrorCode`, `TraceIdFilter`, `GlobalExceptionHandler`, `RateLimitFilter`
- [x] Organizations, Properties, Manager Assignments, Invitations entities/repos/mappers/services/controllers
- [x] Auth & Security hardening (`CustomUserDetailsService`, `OtpService`, `DataSeeder`, backward-compatible `/api/auth/*` + `/api/v1/auth/*`)
- [x] Backend unit & H2 integration tests (13 active tests pass; 2 MySQL integration tests pending local MySQL/Docker)
- [x] Web frontend auth/manager shell & property switcher (`tsc`, `vite build`, modified-file ESLint pass)
- [x] Expo mobile foundation (`Upcraft-PG-mobile`) + `tsc` + Vitest tests (`5/5` pass)

## Change History

| Date | Change Description | Old Requirement (Commented) |
| --- | --- | --- |
| 11-11-2026 | Initial Milestone 1 plan created | — |
| 11-11-2026 | Milestone 1 implementation completed pending live MySQL check | — |
