# Upcraft PG Backend (`Upcraft-PG`)

Spring Boot 4.1.0 (Java 21) + MySQL 8 + Flyway multi-tenant PG management backend powering Web, Android, and iOS clients.

## Setup & Environment

1. Copy `.env.example` and configure required variables (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ALLOWED_ORIGINS`, optional `APP_UPLOAD_DIR` defaulting to `./uploads/private`, optional `APP_NOTIF_EMAIL_ENABLED`, `EXPO_ACCESS_TOKEN`, `SMS_PROVIDER`/`SMS_API_KEY`, `WHATSAPP_PROVIDER`/`WHATSAPP_API_KEY`).
2. Ensure MySQL 8+ is running and reachable.
3. Run locally (`dev` profile):
   `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

## Safe Flyway Migration Strategy

- `spring.flyway.baseline-on-migrate=true`, `baseline-version=1`.
- Existing un-versioned schemas baseline safely at `V1` without dropping data, then execute `V2`–`V13`.
- Fresh empty databases run `V1` through `V13` sequentially.
- `spring.jpa.hibernate.ddl-auto=validate` prevents uncontrolled runtime DDL mutations.

## Testing

- Offline unit & H2 API/security tests: `mvn test`
- Real MySQL Flyway migration suite (fresh + baselined): set `MYSQL_INTEGRATION_TEST=true` and `MYSQL_TEST_PASSWORD=...`, then run `mvn test -Dtest=FlywayMySqlIntegrationTest`

