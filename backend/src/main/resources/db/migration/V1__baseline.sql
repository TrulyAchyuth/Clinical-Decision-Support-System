-- Baseline migration for the CDSS educational prototype.
--
-- Establishes database-wide defaults only. Domain tables (patients, clinical
-- data, users, rules, alerts, audit) are added by later migrations, one
-- feature at a time.
--
-- utf8mb4 stores the full Unicode range; utf8mb4_0900_ai_ci is the MySQL 8
-- default collation. Tables created by later migrations inherit these.

ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
