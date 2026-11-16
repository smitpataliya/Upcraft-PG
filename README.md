# Upcraft PG Backend (`Upcraft-PG`)

Multi-tenant Paying Guest (PG) & Hostel Management Platform built with **Java 21**, **Spring Boot 4.1.0**, **MySQL 8+**, **Flyway (`V1`–`V15`)**, **Spring Security + JWT**, **MapStruct**, **Caffeine**, **OpenPDF**, and **Spring Boot Actuator**.

## Core Capabilities (Milestones M1–M7)

- **Foundation & RBAC (M1):** `PG_OWNER`, `MANAGER` (property-scoped), and `PG_MEMBER` (self-scoped) access enforcement via `AccessGuardService`, JWT auth, Trace-ID MDC, invitation tokens, 8-column audit fields, `BINARY(16)` UUID PKs.
- **Multi-Property & Occupancy (M2):** Properties, buildings, floors, rooms, beds, stored generated-column uniqueness (`active_guard`, `active_bed_guard`), move-in/transfer/move-out wizards.
- **Billing, Payments & Deposits (M3):** Property/room rent defaults, idempotent monthly billing runs, partial payments, append-only payment reversals, ledger entries, deposit settlement.
- **Electricity & Splits (M4):** Meters, meter readings with evidence files, tariff snapshots, equal/bed-ratio splits into invoice line items.
- **Operations & Notifications (M5):** Complaints lifecycle, announcements, food menu, private encrypted-at-rest local/provider document storage, async Expo Push / SMTP / SMS adapters.
- **Reporting & Sharing (M6):** Live DB Owner/Manager/Tenant dashboards, 9 filterable report types, CSV & OpenPDF exports, public expiring non-PII share links (`/api/v1/public/share/**`).
- **Hardening, Search, Audit & Containers (M7):**
  - Immutable `audit_logs` (`V15__create_audit_logs_table.sql`, `AuditLogService`, `AuditSanitizer`, `GET /api/v1/audit-logs` for `PG_OWNER` & assigned-property `MANAGER`).
  - Global Search (`GET /api/v1/search?q=&propertyId=`), bounded to 5 results per category (`Tenants`, `Rooms & Beds`, `Invoices`, `Payments`, `Complaints`), strictly scoped by role/org/property/tenant ownership.
  - Proxy-safe tiered Caffeine rate limiting (`auth`, `publicShare`, `upload`, `sensitive`) with CIDR validation (`app.rate-limit.trusted-proxies`).
  - Health checks via Spring Boot Actuator (`/actuator/health`).

## Scaling & Rate Limiting Notes

- **Single-Instance Rate Limiting:** `RateLimitFilter` uses in-memory bounded **Caffeine** caches (`maximumSize = 10,000`, 60s expiry). Limits are **JVM-instance-local**; horizontal scaling across multiple backend replicas requires replacing/backing these buckets with a distributed store (e.g., Redis + Bucket4j).
- **Performance Targets (Unverified Design Targets):** The platform is sized as a design target around up to `5,000` users, `20` average RPS, `200` peak RPS, and read/search `p95 < 120ms`. Run [`scripts/load-test.js`](scripts/load-test.js) with `k6` against a staging instance to collect measured benchmarks.

## Local Development & Verification

```powershell
# Compile
.\mvnw.cmd compile

# Run test suite (H2 MySQL mode)
.\mvnw.cmd test

# Optional MySQL 8 Flyway dual-path verification (requires live local MySQL + env creds)
$env:MYSQL_INTEGRATION_TEST="true"
.\mvnw.cmd test -Dtest=FlywayMySqlIntegrationTest
```

## Key Environment Variables

| Variable | Description | Default / Example |
|---|---|---|
| `DB_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/pg_management?...` |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials | Required externally |
| `APP_JWT_SECRET` | Base64 >=256-bit HMAC key | Required in production |
| `APP_CORS_ALLOWED_ORIGINS` | Allowed Web origins | `http://localhost:3000,http://localhost:5173` |
| `APP_RATE_LIMIT_TRUSTED_PROXIES` | Trusted proxy IPs / CIDRs for `X-Forwarded-For` | `127.0.0.1,::1,172.16.0.0/12,10.0.0.0/8` |
| `APP_STORAGE_LOCAL_BASE_DIR` | Local uploaded file storage path | `uploads` (`/app/uploads` in Docker) |
| `APP_NOTIF_EMAIL_ENABLED` | Enable SMTP email dispatch | `false` |

## Docker Compose & Backup / Restore

Run the stack from the workspace root (`d:\Upcraft\workspace`):

```bash
cp .env.docker.example .env.docker
# Edit .env.docker with strong secrets
docker compose --env-file .env.docker up -d --build
```

MySQL (`mysql:8.0`) runs **exclusively on the internal `upcraft_internal` Docker network** with no published host ports.

### Backup & Isolated Restore Verification

```bash
# Backup (reads MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD from env)
./scripts/backup-mysql.sh

# Safe restore verification into scratch schema (never overwrites live pg_management by default)
./scripts/restore-mysql.sh ./backups/pg_management-<timestamp>.sql.gz pg_management_restore_verify
```

Windows PowerShell equivalents live at [`scripts/backup-mysql.ps1`](scripts/backup-mysql.ps1) and [`scripts/restore-mysql.ps1`](scripts/restore-mysql.ps1).
