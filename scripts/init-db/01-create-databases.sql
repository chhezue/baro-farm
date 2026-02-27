-- ============================================================
-- MySQL 초기 데이터베이스 생성 및 권한 부여 (MySQL 8 호환)
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `baro_ai` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_notification` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_payment` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_settlement` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_shopping` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_order` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_user` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- docker-compose의 MYSQL_USER/MYSQL_PASSWORD로 이미 생성되어 있어도 안전
CREATE USER IF NOT EXISTS 'barouser'@'%' IDENTIFIED BY 'baropassword';
CREATE USER IF NOT EXISTS 'barouser'@'localhost' IDENTIFIED BY 'baropassword';

GRANT ALL PRIVILEGES ON `baro_ai`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_notification`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_payment`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_settlement`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_shopping`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_order`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_user`.* TO 'barouser'@'%';

GRANT ALL PRIVILEGES ON `baro_ai`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_notification`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_payment`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_settlement`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_shopping`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_order`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_user`.* TO 'barouser'@'localhost';

FLUSH PRIVILEGES;
