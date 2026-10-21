-- ==============================================================================
-- Grant Remote Access Privileges for MariaDB (Wildcard Host '%')
-- Ensures Host Machine IDE / Remote CLI can connect without 'Access Denied'
-- Compatible with MariaDB 11.2+ / MySQL 8.0+
-- ==============================================================================

USE hotel_prod_db;

-- 1. Ensure user 'hotel_admin' exists with wildcard host '%'
CREATE USER IF NOT EXISTS 'hotel_admin'@'%' IDENTIFIED BY 'hotel_secret_pass';
ALTER USER 'hotel_admin'@'%' IDENTIFIED BY 'hotel_secret_pass';

-- 2. Grant full operational privileges on hotel_prod_db
GRANT ALL PRIVILEGES ON hotel_prod_db.* TO 'hotel_admin'@'%';
GRANT ALL PRIVILEGES ON `hotel_prod_db`.* TO 'hotel_admin'@'%';

-- 3. Also grant local loopback privileges
CREATE USER IF NOT EXISTS 'hotel_admin'@'localhost' IDENTIFIED BY 'hotel_secret_pass';
GRANT ALL PRIVILEGES ON hotel_prod_db.* TO 'hotel_admin'@'localhost';

-- 4. Flush privileges immediately to apply changes
FLUSH PRIVILEGES;

-- 5. Verification query
SELECT User, Host FROM mysql.user WHERE User = 'hotel_admin';
