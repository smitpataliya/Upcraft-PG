# Upcraft PG Backend — Milestone 3: Financials (Rent Rules, Invoices, Payments, Receipts, Ledger & Deposits) Plan

## Status

Done (verified: 22 passed / 0 failed / 3 MySQL-env skipped in Backend `mvn test`; Web `vite build` + `tsc --noEmit` + `eslint` exit 0; Mobile `tsc --noEmit` + 12/12 Vitest + `expo export` exit 0)

## Module Overview

Implements Milestone 3 financial workflows across `Upcraft-PG`, `Upcraft-Shree-krishna-pg-frontend`, and `Upcraft-PG-mobile`:

- Configurable monthly rent billing, due days, grace periods, optional proration, discounts, late fees, and one-time charges per property/tenancy.
- Idempotent monthly rent invoice generation per `(tenancy, year, month)` enforced at both JPA transaction and MySQL unique generated-column (`active_invoice_guard`) levels.
- Full and partial payment recording (`CASH`, `BANK_TRANSFER`, `UPI`, `GATEWAY`, `OTHER`) with overpayment rejection, pessimistic locking (`PESSIMISTIC_WRITE`), auto-generated receipt numbers, and payment gateway boundary interface.
- Immutable payment history using explicit reversal/adjustment ledger rows (`payment_adjustments`) with mandatory reason and actor audit trail.
- Segregated security deposit ledger (`deposit_transactions`) tracking `COLLECTED`, `DEDUCTION`, `REFUND`, `ADJUSTMENT`, and `FORFEIT` separately from rent revenue, auto-populated on move-in and move-out settlement.
- Tenant account ledger summary + paginated ledger entries, resident-only bill viewing (`/api/v1/invoices/mine`), and role-scoped authorization (`PG_OWNER`, assigned `MANAGER` with `canRecordPayments`, `PG_MEMBER` self-only).

## Tech Stack

- Backend: Java 21, Spring Boot 4.1.0, MySQL 8+, Flyway (`V9`–`V10`), Spring Data JPA (`@EntityGraph`, `@Lock`), MapStruct, Jakarta Validation, `BigDecimal` (`DECIMAL(12,2)`).
- Web: React 19, TypeScript, TanStack Start/Router, Tailwind 4, Radix UI, TanStack Query, Zod.
- Mobile: React Native, Expo SDK 58, TypeScript, Expo Router, TanStack Query, RHF + Zod.

## Requirements

### Version 1 — [Date: 12-11-2026]

- Generate monthly rent invoices idempotently per property (or all accessible active properties) for any `billingYear` + `billingMonth`, skipping tenancies already invoiced or inactive during the period.
- Support optional mid-month move-in / move-out proration, custom monthly discounts, and one-time invoice line-item additions (`EXTRA_CHARGE`, `DISCOUNT`, `LATE_FEE`, `UTILITY`).
- Enforce invoice statuses: `UNPAID`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `CANCELLED`, `WAIVED`; exclude `CANCELLED`/`WAIVED` invoices from all outstanding/overdue totals.
- Record multiple/partial payments with `PESSIMISTIC_WRITE` row locking on invoice, prevent overpayment above `balanceDue`, optionally guard retried submissions with `idempotencyKey`, and generate unique `receiptNumber` (`RCP-YYYYMM-XXXX`).
- Never silently edit or delete completed payments; expose `POST /api/v1/payments/{paymentId}/reverse` recording `PaymentAdjustmentEntity` + restoring invoice balance and status.
- Maintain separate security deposit transaction ledger (`deposit_transactions`) synced automatically during `moveIn` / `moveOut` plus explicit deposit collect/deduct/refund endpoint.
- Expose tenant ledger (`GET /api/v1/tenants/{tenantId}/ledger`), printable receipt DTO (`GET /api/v1/payments/{paymentId}/receipt`), and resident self-bills (`GET /api/v1/invoices/mine`).

## Non-Functional Requirements

- **ASSUMPTION (Load & Scale):** 50–500 concurrent users, up to ~200 properties / 10,000 beds / 120,000 invoices/year.
- **Latency Targets (estimate):**
  - Paginated invoice/payment/deposit/ledger reads (`<= 3` indexed queries via `@EntityGraph`, zero N+1): target p95 `<= 200 ms`.
  - Monthly invoice batch generation per property (bounded per property page/batch): single batch fetch of tenancies + existing period keys (`O(n)` memory, zero per-row N+1 lookup), target p95 `<= 600 ms`.
  - Payment recording / reversal (`PESSIMISTIC_WRITE` on single invoice row): target p95 `<= 250 ms`.
- **Money Precision & Timezone:** Strict `BigDecimal` (`scale=2`, `HALF_UP`), `DECIMAL(12,2)` in DB, UTC audit timestamps, `Asia/Kolkata` billing month/overdue date evaluation, leap-year/short-month day clamping.
- **Security:** `PAYMENTS` manager permission required for invoice generation, payment recording, reversals, late fees, and deposit adjustments; `PG_MEMBER` strictly limited to own tenant invoices and receipts.

## Options Considered

| Option   | Approach                                                                                                                                                | Verdict                                                                                                                                |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- |
| Option A | Normalized `invoices`, `invoice_items`, `payments`, `payment_adjustments`, `deposit_transactions` + `active_invoice_guard` + `@Lock(PESSIMISTIC_WRITE)` | **Chosen** — guarantees zero double billing, zero race overpayments, clean deposit vs revenue split, full audit trail                  |
| Option B | Mutable JSON/balance fields directly on `tenancies`                                                                                                     | **Rejected** — fails partial payments, receipts, adjustments/reversals, and deposit separation                                         |
| Option C | Separate async message queue billing engine                                                                                                             | **Rejected** — premature complexity for current modular monolith; idempotent sync + `active_invoice_guard` already prevents duplicates |

## Architecture & Design

- Package structure (`com.upcraft.stay.billing.*`):
  - `entity`: `InvoiceEntity`, `InvoiceItemEntity`, `PaymentEntity`, `PaymentAdjustmentEntity`, `DepositTransactionEntity`
  - `repository`: `InvoiceRepository`, `InvoiceItemRepository`, `PaymentRepository`, `PaymentAdjustmentRepository`, `DepositTransactionRepository`
  - `dto`: records for requests (`GenerateInvoicesRequestDTO`, `CreateCustomInvoiceRequestDTO`, `UpdateInvoiceStatusRequestDTO`, `RecordPaymentRequestDTO`, `ReversePaymentRequestDTO`, `RecordDepositTransactionRequestDTO`) and responses (`InvoiceSummaryResponseDTO`, `InvoiceDetailResponseDTO`, `InvoiceItemResponseDTO`, `PaymentResponseDTO`, `PaymentAdjustmentResponseDTO`, `PaymentReceiptResponseDTO`, `GenerateInvoicesResultResponseDTO`, `FinancialSummaryResponseDTO`, `TenantLedgerResponseDTO`, `DepositAccountSummaryResponseDTO`, `DepositTransactionResponseDTO`)
  - `service` & `service.impl`: `InvoiceService`, `PaymentService`, `DepositService`, `InvoiceGenerationHelper`, `PaymentSettlementHelper`, `PaymentGatewayBoundary` (+ `ManualRecordingGatewayAdapter`)
  - `controller`: `InvoiceV1Controller`, `PaymentV1Controller`, `DepositV1Controller`, `BillingV1Controller`

## API Endpoints

| Method & Path                                 | Roles / Guard                        | Request DTO                                                                    | Response / Status                                   |
| --------------------------------------------- | ------------------------------------ | ------------------------------------------------------------------------------ | --------------------------------------------------- |
| `GET /api/v1/billing/summary`                 | Owner / assigned Manager             | Filters (`propertyId`, `billingYear`, `billingMonth`)                          | `200 OK` (`FinancialSummaryResponseDTO`)            |
| `GET /api/v1/invoices`                        | Owner / assigned Manager             | Filters (`propertyId`, `invoiceStatus`, `tenantId`, `year`, `month`, `search`) | `200 OK` paginated `InvoiceSummaryResponseDTO`      |
| `GET /api/v1/invoices/mine`                   | `PG_MEMBER`                          | Pagination                                                                     | `200 OK` paginated `InvoiceSummaryResponseDTO`      |
| `GET /api/v1/invoices/{invoiceId}`            | Owner / Manager / owning `PG_MEMBER` | —                                                                              | `200 OK` (`InvoiceDetailResponseDTO`)               |
| `POST /api/v1/invoices/generate-rent`         | Owner / Manager (`PAYMENTS`)         | `GenerateInvoicesRequestDTO`                                                   | `201 Created` (`GenerateInvoicesResultResponseDTO`) |
| `POST /api/v1/invoices`                       | Owner / Manager (`PAYMENTS`)         | `CreateCustomInvoiceRequestDTO`                                                | `201 Created` (`InvoiceDetailResponseDTO`)          |
| `PATCH /api/v1/invoices/{invoiceId}/status`   | Owner / Manager (`PAYMENTS`)         | `UpdateInvoiceStatusRequestDTO`                                                | `200 OK` (`InvoiceDetailResponseDTO`)               |
| `POST /api/v1/invoices/{invoiceId}/late-fee`  | Owner / Manager (`PAYMENTS`)         | —                                                                              | `200 OK` (`InvoiceDetailResponseDTO`)               |
| `GET /api/v1/payments`                        | Owner / assigned Manager             | Filters (`propertyId`, `paymentStatus`, `method`, `invoiceId`, `tenantId`)     | `200 OK` paginated `PaymentResponseDTO`             |
| `POST /api/v1/invoices/{invoiceId}/payments`  | Owner / Manager (`PAYMENTS`)         | `RecordPaymentRequestDTO`                                                      | `201 Created` (`PaymentResponseDTO`)                |
| `POST /api/v1/payments/{paymentId}/reverse`   | Owner / Manager (`PAYMENTS`)         | `ReversePaymentRequestDTO`                                                     | `200 OK` (`PaymentResponseDTO`)                     |
| `GET /api/v1/payments/{paymentId}/receipt`    | Owner / Manager / owning `PG_MEMBER` | —                                                                              | `200 OK` (`PaymentReceiptResponseDTO`)              |
| `GET /api/v1/tenants/{tenantId}/ledger`       | Owner / assigned Manager             | Pagination                                                                     | `200 OK` (`TenantLedgerResponseDTO`)                |
| `GET /api/v1/tenancies/{tenancyId}/deposits`  | Owner / Manager / owning `PG_MEMBER` | Pagination                                                                     | `200 OK` (`DepositAccountSummaryResponseDTO`)       |
| `POST /api/v1/tenancies/{tenancyId}/deposits` | Owner / Manager (`PAYMENTS`)         | `RecordDepositTransactionRequestDTO`                                           | `201 Created` (`DepositTransactionResponseDTO`)     |

## Database Design

- **`V9__create_invoices_and_payments_tables.sql`**: Creates `invoices` (`active_invoice_guard` + unique `(organization_id, invoice_number, active_guard)`), `invoice_items`, `payments` (`active_idempotency_guard` + unique `receipt_number` per org), and `payment_adjustments` — all with 8 mandatory audit columns and FK indexes.
- **`V10__create_deposit_transactions_and_backfill.sql`**: Creates `deposit_transactions` with 8 mandatory audit columns and idempotently backfills existing M2 tenancy deposits/deductions/refunds.

## Implementation Checklist

- [x] Architecture & API plan created
- [x] SQL documentation & Flyway `V9`–`V10` migrations created
- [x] Billing enums, entities, repositories, mapper & payment gateway boundary implemented
- [x] Invoice generation, custom charge/waiver/late-fee, payment recording/reversal, deposit & ledger services/controllers implemented
- [x] Unit tests + `Milestone3FinancialsIntegrationTest` passing
- [x] Web frontend (`Upcraft-Shree-krishna-pg-frontend`) billing, invoices, receipts, ledger & deposits screens wired
- [x] Mobile app (`Upcraft-PG-mobile`) Owner/Manager Billing & Deposits + Tenant Bills & Deposit statement wired
- [x] Full backend/web/mobile build & test regression verified

## Change History

| Date       | Change Description               | Old Requirement (Commented) |
| ---------- | -------------------------------- | --------------------------- |
| 12-11-2026 | Initial Milestone 3 plan created | —                           |
