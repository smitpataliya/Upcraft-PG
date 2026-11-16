package com.upcraft.stay.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.upcraft.stay.StayApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real MySQL 8+ Flyway migration and schema verification suite.
 * Never mutates {@code pg_management}; executes migrations only against isolated test schemas.
 */
@Tag("mysql-integration")
@EnabledIfSystemProperty(named = "mysql.integration.test", matches = "true")
class FlywayMySqlIntegrationTest {

    @Test
    void inspectExistingPgManagementSchema_readOnlyCheck() throws Exception {
        String baseJdbc = buildBaseJdbcUrl();
        try (Connection connection = DriverManager.getConnection(baseJdbc, dbUser(), dbPassword())) {
            String mysqlVersion = queryScalar(connection, "SELECT VERSION()");
            System.out.println("Connected MySQL version: " + mysqlVersion);

            boolean pgDbExists;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'pg_management'"
            ); ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                pgDbExists = resultSet.getInt(1) > 0;
            }

            if (!pgDbExists) {
                System.out.println("Schema pg_management does not exist yet on local MySQL80.");
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'pg_management'
                      AND (
                        (TABLE_NAME = 'users' AND COLUMN_NAME IN ('id', 'role', 'room_id'))
                        OR (TABLE_NAME = 'rooms' AND COLUMN_NAME IN ('id', 'number', 'price'))
                      )
                    ORDER BY TABLE_NAME, ORDINAL_POSITION
                    """
            ); ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    System.out.printf("pg_management.%s.%s -> type=%s nullable=%s key=%s%n",
                            resultSet.getString("TABLE_NAME"),
                            resultSet.getString("COLUMN_NAME"),
                            resultSet.getString("COLUMN_TYPE"),
                            resultSet.getString("IS_NULLABLE"),
                            resultSet.getString("COLUMN_KEY"));
                }
            }

            try (PreparedStatement tablesStmt = connection.prepareStatement(
                    "SELECT TABLE_NAME, TABLE_ROWS FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'pg_management' ORDER BY TABLE_NAME"
            ); ResultSet tablesRs = tablesStmt.executeQuery()) {
                while (tablesRs.next()) {
                    System.out.printf("pg_management table: %s (estRows=%d)%n",
                            tablesRs.getString("TABLE_NAME"), tablesRs.getLong("TABLE_ROWS"));
                }
            }
        }
    }

    @Test
    void freshDatabase_appliesV1ThroughV4_validatesHibernateAndActiveGuard() throws Exception {
        String freshSchema = "upcraft_pg_fresh_verify";
        recreateIsolatedSchema(freshSchema);
        String schemaJdbc = buildSchemaJdbcUrl(freshSchema);

        Flyway flyway = Flyway.configure()
                .dataSource(schemaJdbc, dbUser(), dbPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        var result = flyway.migrate();
        assertThat(result.migrationsExecuted).isEqualTo(15);
        assertThat(readFlywayVersions(schemaJdbc)).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"
        );

        verifyActiveGuardUniqueness(schemaJdbc);
        verifyHibernateValidateBoot(schemaJdbc);
    }

    @Test
    void existingSchemaWithLegacyRows_baselinesAtV1_preservesDataAndValidatesHibernate() throws Exception {
        String baselineSchema = "upcraft_pg_baseline_verify";
        recreateIsolatedSchema(baselineSchema);
        String schemaJdbc = buildSchemaJdbcUrl(baselineSchema);

        seedSimulatedLegacyHibernateSchema(schemaJdbc);

        Flyway flyway = Flyway.configure()
                .dataSource(schemaJdbc, dbUser(), dbPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();

        var result = flyway.migrate();
        assertThat(result.migrationsExecuted).isEqualTo(14);
        assertThat(readFlywayVersions(schemaJdbc)).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"
        );

        // Running migrate again must execute 0 additional migrations and remain idempotent
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (Connection connection = DriverManager.getConnection(schemaJdbc, dbUser(), dbPassword())) {
            assertThat(countRows(connection, "users")).isEqualTo(2);
            assertThat(countRows(connection, "rooms")).isEqualTo(1);
            assertThat(countRows(connection, "complaints")).isEqualTo(1);
            assertThat(countRows(connection, "queries")).isEqualTo(1);
            assertThat(countRows(connection, "meals")).isEqualTo(1);
            assertThat(countRows(connection, "announcements")).isEqualTo(1);
            assertThat(countRows(connection, "notifications")).isEqualTo(1);

            assertThat(countRows(connection, "organizations")).isEqualTo(1);
            assertThat(countRows(connection, "properties")).isEqualTo(1);
            assertThat(countRows(connection, "organization_members")).isEqualTo(2);
            assertThat(countRows(connection, "beds")).isEqualTo(2);
            assertThat(countRows(connection, "tenants")).isEqualTo(1);
            assertThat(countRows(connection, "tenancies")).isEqualTo(1);
        }

        verifyHibernateValidateBoot(schemaJdbc);
    }

    private void verifyActiveGuardUniqueness(String schemaJdbc) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaJdbc, dbUser(), dbPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO users (id, name, email, role, active, created_at)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Owner Guard', 'guard.owner@pg.test', 'PG_OWNER', b'1', UTC_TIMESTAMP(6))
                    """);
            String ownerHex = queryScalar(connection,
                    "SELECT HEX(id) FROM users WHERE email = 'guard.owner@pg.test'");

            // First active org with slug 'alpha-pg' succeeds
            statement.execute("""
                    INSERT INTO organizations (id, name, slug, owner_user_id, timezone, currency_code, version, created_at, status)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Alpha PG', 'alpha-pg', UNHEX('%s'), 'Asia/Kolkata', 'INR', 0, UTC_TIMESTAMP(6), 1)
                    """.formatted(ownerHex));

            // Second active org with same slug 'alpha-pg' MUST FAIL
            assertThatThrownBy(() -> statement.execute("""
                    INSERT INTO organizations (id, name, slug, owner_user_id, timezone, currency_code, version, created_at, status)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Alpha PG Dup', 'alpha-pg', UNHEX('%s'), 'Asia/Kolkata', 'INR', 0, UTC_TIMESTAMP(6), 1)
                    """.formatted(ownerHex)))
                    .isInstanceOf(SQLIntegrityConstraintViolationException.class);

            // Soft-delete first org (status = 9) -> active_guard becomes NULL -> re-using slug 'alpha-pg' MUST SUCCEED
            statement.execute("UPDATE organizations SET status = 9, deleted_at = UTC_TIMESTAMP(6) WHERE slug = 'alpha-pg'");
            statement.execute("""
                    INSERT INTO organizations (id, name, slug, owner_user_id, timezone, currency_code, version, created_at, status)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Alpha PG Reborn', 'alpha-pg', UNHEX('%s'), 'Asia/Kolkata', 'INR', 0, UTC_TIMESTAMP(6), 1)
                    """.formatted(ownerHex));
        }
    }

    private void verifyHibernateValidateBoot(String schemaJdbc) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(StayApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + schemaJdbc,
                        "spring.datasource.username=" + dbUser(),
                        "spring.datasource.password=" + dbPassword(),
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.flyway.enabled=true",
                        "spring.flyway.baseline-on-migrate=true",
                        "app.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LWZvci1qdW5pdC10ZXN0cy0xMjM0NTY3ODkwMTIzNDU2Nzg5MA=="
                )
                .run()) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private void seedSimulatedLegacyHibernateSchema(String schemaJdbc) throws Exception {
        try (Connection connection = DriverManager.getConnection(schemaJdbc, dbUser(), dbPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE rooms (
                        id BINARY(16) NOT NULL PRIMARY KEY,
                        number VARCHAR(255) NOT NULL UNIQUE,
                        floor INT NOT NULL,
                        sharing VARCHAR(255) NOT NULL,
                        capacity INT NOT NULL,
                        occupied INT NOT NULL DEFAULT 0,
                        price DOUBLE NOT NULL,
                        created_at DATETIME(6) NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE room_amenities (
                        room_id BINARY(16) NOT NULL,
                        amenity VARCHAR(255) NULL,
                        CONSTRAINT fk_legacy_room_amenities FOREIGN KEY (room_id) REFERENCES rooms (id)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE users (
                        id BINARY(16) NOT NULL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        email VARCHAR(255) NOT NULL UNIQUE,
                        username VARCHAR(255) NULL UNIQUE,
                        password VARCHAR(255) NULL,
                        phone VARCHAR(255) NULL,
                        google_id VARCHAR(255) NULL,
                        avatar_url VARCHAR(255) NULL,
                        role ENUM('SUPER_ADMIN','PG_OWNER','PG_MEMBER') NOT NULL,
                        active BIT(1) NOT NULL DEFAULT b'1',
                        emergency_contact VARCHAR(255) NULL,
                        address VARCHAR(255) NULL,
                        bed_number VARCHAR(255) NULL,
                        check_in_date DATE NULL,
                        room_id BINARY(16) NULL,
                        created_at DATETIME(6) NULL,
                        updated_at DATETIME(6) NULL,
                        CONSTRAINT fk_legacy_users_room FOREIGN KEY (room_id) REFERENCES rooms (id)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE meals (
                        id BINARY(16) NOT NULL PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        description VARCHAR(255) NULL,
                        image_url VARCHAR(255) NULL,
                        type ENUM('BREAKFAST','LUNCH','SNACKS','DINNER') NOT NULL,
                        serving_time VARCHAR(255) NULL,
                        veg BIT(1) NOT NULL DEFAULT b'1',
                        available BIT(1) NOT NULL DEFAULT b'1',
                        date_scheduled DATE NOT NULL,
                        created_at DATETIME(6) NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE announcements (
                        id BINARY(16) NOT NULL PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        body TEXT NOT NULL,
                        category VARCHAR(255) NULL,
                        pinned BIT(1) NOT NULL DEFAULT b'0',
                        author VARCHAR(255) NOT NULL,
                        created_at DATETIME(6) NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE complaints (
                        id BINARY(16) NOT NULL PRIMARY KEY,
                        title VARCHAR(255) NOT NULL,
                        description TEXT NOT NULL,
                        category ENUM('WATER','ELECTRICITY','CLEANING','FOOD','INTERNET','FURNITURE','OTHER') NOT NULL,
                        priority ENUM('LOW','MEDIUM','HIGH') NOT NULL,
                        status ENUM('PENDING','IN_PROGRESS','RESOLVED','REJECTED') NOT NULL,
                        member_id BINARY(16) NOT NULL,
                        created_at DATETIME(6) NULL,
                        updated_at DATETIME(6) NULL,
                        CONSTRAINT fk_legacy_complaints_member FOREIGN KEY (member_id) REFERENCES users (id)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE complaint_timeline (
                        id BINARY(16) NOT NULL PRIMARY KEY,
                        label VARCHAR(255) NOT NULL,
                        updated_by VARCHAR(255) NULL,
                        at DATETIME(6) NULL,
                        complaint_id BINARY(16) NOT NULL,
                        CONSTRAINT fk_legacy_ct_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE queries (
                        id BINARY(16) NOT NULL PRIMARY KEY,
                        subject VARCHAR(255) NOT NULL,
                        description TEXT NOT NULL,
                        category ENUM('ROOM_CHANGE','FOOD_REQUEST','VISITOR','LEAVE','GENERAL') NOT NULL,
                        status ENUM('OPEN','REPLIED','CLOSED') NOT NULL,
                        member_id BINARY(16) NOT NULL,
                        created_at DATETIME(6) NULL,
                        updated_at DATETIME(6) NULL,
                        CONSTRAINT fk_legacy_queries_member FOREIGN KEY (member_id) REFERENCES users (id)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE query_replies (
                        id BINARY(16) NOT NULL PRIMARY KEY,
                        message TEXT NOT NULL,
                        sender_name VARCHAR(255) NOT NULL,
                        sender_role ENUM('SUPER_ADMIN','PG_OWNER','PG_MEMBER') NOT NULL,
                        query_id BINARY(16) NOT NULL,
                        at DATETIME(6) NULL,
                        CONSTRAINT fk_legacy_qr_query FOREIGN KEY (query_id) REFERENCES queries (id)
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE notifications (
                        id BINARY(16) NOT NULL PRIMARY KEY,
                        user_id BINARY(16) NOT NULL,
                        title VARCHAR(255) NOT NULL,
                        message VARCHAR(1000) NOT NULL,
                        type VARCHAR(255) NULL,
                        is_read BIT(1) NOT NULL DEFAULT b'0',
                        created_at DATETIME(6) NULL,
                        CONSTRAINT fk_legacy_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
                    ) ENGINE=InnoDB
                    """);

            // Insert sample legacy records
            statement.execute("""
                    INSERT INTO rooms (id, number, floor, sharing, capacity, occupied, price, created_at)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), '101', 1, '2-Sharing', 2, 1, 8500.0, UTC_TIMESTAMP(6))
                    """);
            String roomHex = queryScalar(connection, "SELECT HEX(id) FROM rooms WHERE number = '101'");

            statement.execute("""
                    INSERT INTO users (id, name, email, password, role, active, created_at)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Legacy Owner', 'legacy.owner@pg.com', '$2a$10$hash', 'PG_OWNER', b'1', UTC_TIMESTAMP(6))
                    """);
            statement.execute("""
                    INSERT INTO users (id, name, email, password, role, active, bed_number, check_in_date, room_id, created_at)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Legacy Tenant', 'legacy.member@pg.com', '$2a$10$hash', 'PG_MEMBER', b'1', 'B1', '2026-01-01', UNHEX('%s'), UTC_TIMESTAMP(6))
                    """.formatted(roomHex));
            String memberHex = queryScalar(connection, "SELECT HEX(id) FROM users WHERE email = 'legacy.member@pg.com'");

            statement.execute("""
                    INSERT INTO meals (id, name, type, veg, available, date_scheduled, created_at)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Poha', 'BREAKFAST', b'1', b'1', '2026-11-11', UTC_TIMESTAMP(6))
                    """);
            statement.execute("""
                    INSERT INTO announcements (id, title, body, pinned, author, created_at)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Water Tank Cleaning', 'Tomorrow 10 AM', b'1', 'Legacy Owner', UTC_TIMESTAMP(6))
                    """);
            statement.execute("""
                    INSERT INTO complaints (id, title, description, category, priority, status, member_id, created_at)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Tap leaking', 'Bathroom tap', 'WATER', 'MEDIUM', 'PENDING', UNHEX('%s'), UTC_TIMESTAMP(6))
                    """.formatted(memberHex));
            statement.execute("""
                    INSERT INTO queries (id, subject, description, category, status, member_id, created_at)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'Guest stay', 'Parent visit', 'VISITOR', 'OPEN', UNHEX('%s'), UTC_TIMESTAMP(6))
                    """.formatted(memberHex));
            statement.execute("""
                    INSERT INTO notifications (id, user_id, title, message, is_read, created_at)
                    VALUES (UNHEX(REPLACE(UUID(), '-', '')), UNHEX('%s'), 'Welcome', 'Room assigned', b'0', UTC_TIMESTAMP(6))
                    """.formatted(memberHex));
        }
    }

    private void recreateIsolatedSchema(String schemaName) throws SQLException {
        try (Connection connection = DriverManager.getConnection(buildBaseJdbcUrl(), dbUser(), dbPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + schemaName);
            statement.execute("CREATE DATABASE " + schemaName + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private List<String> readFlywayVersions(String schemaJdbc) throws SQLException {
        List<String> versions = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(schemaJdbc, dbUser(), dbPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank ASC"
             )) {
            while (resultSet.next()) {
                versions.add(resultSet.getString(1));
            }
        }
        return versions;
    }

    private int countRows(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String queryScalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private String buildBaseJdbcUrl() {
        return String.format("jdbc:mysql://%s:%s/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                envOrDefault("MYSQL_TEST_HOST", "localhost"),
                envOrDefault("MYSQL_TEST_PORT", "3306"));
    }

    private String buildSchemaJdbcUrl(String schema) {
        return String.format("jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                envOrDefault("MYSQL_TEST_HOST", "localhost"),
                envOrDefault("MYSQL_TEST_PORT", "3306"),
                schema);
    }

    private String dbUser() {
        return envOrDefault("MYSQL_TEST_USER", "root");
    }

    private String dbPassword() {
        String propValue = System.getProperty("mysql.test.password");
        if (propValue != null) {
            return propValue;
        }
        return envOrDefault("MYSQL_TEST_PASSWORD", "");
    }

    private String envOrDefault(String key, String fallback) {
        String propKey = key.toLowerCase().replace('_', '.');
        String propValue = System.getProperty(propKey);
        if (propValue != null && !propValue.isBlank()) {
            return propValue;
        }
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
