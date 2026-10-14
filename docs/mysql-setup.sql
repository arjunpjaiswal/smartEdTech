-- =============================================================================
-- SmartEdTech: MySQL Database & Dedicated Non-Root User Setup Script
-- =============================================================================
-- Usage:
--   mysql -u root -p < docs/mysql-setup.sql
--
-- Note: Replace 'CHANGE_ME_BEFORE_RUNNING' with your actual secure database password
--       before running this script, and mirror it in your .env file.
-- =============================================================================

-- 1. Create Application and Test Databases (utf8mb4 encoding)
CREATE DATABASE IF NOT EXISTS smartedtech
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS smartedtech_test
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. Create Dedicated Least-Privilege Non-Root Application User
-- IMPORTANT: Change 'CHANGE_ME_BEFORE_RUNNING' to your secret database password!
CREATE USER IF NOT EXISTS 'smartedtech_user'@'localhost' IDENTIFIED BY 'CHANGE_ME_BEFORE_RUNNING';

-- 3. Grant Privileges ONLY on Application and Test Schemas
GRANT ALL PRIVILEGES ON smartedtech.* TO 'smartedtech_user'@'localhost';
GRANT ALL PRIVILEGES ON smartedtech_test.* TO 'smartedtech_user'@'localhost';

-- 4. Flush Privileges
FLUSH PRIVILEGES;
