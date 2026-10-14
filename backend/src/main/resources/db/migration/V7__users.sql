-- Login accounts for the two roles.
--
-- Passwords are stored only as BCrypt hashes. No accounts are inserted here,
-- so no password (or hash of a known password) is ever kept in a migration:
-- in the dev profile the demo users are created at startup from .env
-- (see DemoUserInitializer). The default collation makes usernames
-- case-insensitive, so "Admin" and "admin" cannot both exist.
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'CLINICIAN'))
);
