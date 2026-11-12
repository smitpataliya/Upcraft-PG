-- V1: Baseline existing schema for fresh databases (MySQL 8+)
-- Existing populated databases baseline at version 1 via flyway.baseline-on-migrate=true.

CREATE TABLE IF NOT EXISTS rooms (
    id BINARY(16) NOT NULL,
    number VARCHAR(255) NOT NULL,
    floor INT NOT NULL,
    sharing VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    occupied INT NOT NULL DEFAULT 0,
    price DOUBLE NOT NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rooms_number (number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS room_amenities (
    room_id BINARY(16) NOT NULL,
    amenity VARCHAR(255) NULL,
    KEY idx_room_amenities_room_id (room_id),
    CONSTRAINT fk_room_amenities_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    username VARCHAR(255) NULL,
    password VARCHAR(255) NULL,
    phone VARCHAR(255) NULL,
    google_id VARCHAR(255) NULL,
    avatar_url VARCHAR(255) NULL,
    role VARCHAR(50) NOT NULL,
    active BIT(1) NOT NULL DEFAULT b'1',
    emergency_contact VARCHAR(255) NULL,
    address VARCHAR(255) NULL,
    bed_number VARCHAR(255) NULL,
    check_in_date DATE NULL,
    room_id BINARY(16) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_room_id (room_id),
    CONSTRAINT fk_users_room FOREIGN KEY (room_id) REFERENCES rooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS meals (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    image_url VARCHAR(255) NULL,
    type VARCHAR(50) NOT NULL,
    serving_time VARCHAR(255) NULL,
    veg BIT(1) NOT NULL DEFAULT b'1',
    available BIT(1) NOT NULL DEFAULT b'1',
    date_scheduled DATE NOT NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_meals_date_scheduled (date_scheduled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS announcements (
    id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    category VARCHAR(255) NULL,
    pinned BIT(1) NOT NULL DEFAULT b'0',
    author VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_announcements_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS complaints (
    id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    member_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_complaints_member_id (member_id),
    KEY idx_complaints_status (status),
    CONSTRAINT fk_complaints_member FOREIGN KEY (member_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS complaint_timeline (
    id BINARY(16) NOT NULL,
    label VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255) NULL,
    at DATETIME(6) NULL,
    complaint_id BINARY(16) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_complaint_timeline_complaint_id (complaint_id),
    CONSTRAINT fk_complaint_timeline_complaint FOREIGN KEY (complaint_id) REFERENCES complaints (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS queries (
    id BINARY(16) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    member_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_queries_member_id (member_id),
    KEY idx_queries_status (status),
    CONSTRAINT fk_queries_member FOREIGN KEY (member_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS query_replies (
    id BINARY(16) NOT NULL,
    message TEXT NOT NULL,
    sender_name VARCHAR(255) NOT NULL,
    sender_role VARCHAR(50) NOT NULL,
    query_id BINARY(16) NOT NULL,
    at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_query_replies_query_id (query_id),
    CONSTRAINT fk_query_replies_query FOREIGN KEY (query_id) REFERENCES queries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    type VARCHAR(255) NULL,
    is_read BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user_read (user_id, is_read),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

