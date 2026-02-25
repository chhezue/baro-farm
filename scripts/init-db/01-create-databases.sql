-- MySQL 데이터베이스 초기화 스크립트
-- 각 모듈별 데이터베이스 생성 및 권한 부여

-- 데이터베이스 생성 (없으면 생성)
CREATE DATABASE IF NOT EXISTS `baro_ai` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_notification` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_payment` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_settlement` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_shopping` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_order` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `baro_user` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- barouser에게 모든 데이터베이스에 대한 모든 권한 부여
-- '%'는 모든 호스트에서 접속 가능하도록 설정
GRANT ALL PRIVILEGES ON `baro_ai`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_notification`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_payment`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_settlement`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_shopping`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_order`.* TO 'barouser'@'%';
GRANT ALL PRIVILEGES ON `baro_user`.* TO 'barouser'@'%';

-- localhost에서도 접속 가능하도록 권한 부여
GRANT ALL PRIVILEGES ON `baro_ai`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_notification`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_payment`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_settlement`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_shopping`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_order`.* TO 'barouser'@'localhost';
GRANT ALL PRIVILEGES ON `baro_user`.* TO 'barouser'@'localhost';

-- 권한 변경사항 즉시 적용
FLUSH PRIVILEGES;

