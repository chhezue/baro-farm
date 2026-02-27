-- ============================================================
-- 카테고리 초기 데이터 SQL 스크립트 (UTF-8)
-- ============================================================

USE `baro_shopping`;
SET NAMES utf8mb4;

-- categories 테이블이 없으면 생성
CREATE TABLE IF NOT EXISTS `categories` (
  `id` BINARY(16) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `code` VARCHAR(100) NOT NULL,
  `parent_id` BINARY(16) DEFAULT NULL,
  `level` TINYINT NOT NULL,
  `sort_order` INT NOT NULL DEFAULT 0,
  `created_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categories_code` (`code`),
  KEY `idx_categories_parent_id` (`parent_id`),
  KEY `idx_categories_level_sort` (`level`, `sort_order`),
  CONSTRAINT `fk_categories_parent` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM categories WHERE level = 2;
DELETE FROM categories WHERE level = 1;
DELETE FROM categories WHERE level = 0;
SET FOREIGN_KEY_CHECKS = 1;

-- 고정 UUID
SET @uuid_root              = '550e8400-e29b-41d4-a716-446655440000';
SET @uuid_fruit             = '6ba7b810-9dad-41d1-80b4-00c04fd430c8';
SET @uuid_vegetable         = '7c8d9e0f-1a2b-42e2-91c5-11d2e3f4a5b6';
SET @uuid_grain             = '8d9e0f1a-2b3c-43f3-a2d6-22e3f4a5b6c7';
SET @uuid_special           = '9e0f1a2b-3c4d-44a4-b3e7-33f4a5b6c7d8';
SET @uuid_nut               = 'a01a2b3c-4d5e-45b5-c4f8-44a5b6c7d8e9';
SET @uuid_local_specialty   = 'b12b3c4d-5e6f-46c6-d5a9-55b6c7d8e9f0';
SET @uuid_livestock         = 'c23c4d5e-6f7a-47d7-e6b0-66c7d8e9f0a1';

SET @uuid_fruit_seasonal    = 'd34d5e6f-7a8b-48e8-f7c1-77d8e9f0a1b2';
SET @uuid_fruit_storage     = 'e45e6f7a-8b9c-49f9-a8d2-88e9f0a1b2c3';
SET @uuid_fruit_berry       = 'f56f7a8b-9c0d-4a0a-b9e3-99f0a1b2c3d4';
SET @uuid_veg_salad         = '067a8b9c-0d1e-4b1b-c0f4-00a1b2c3d4e5';
SET @uuid_veg_soup          = '178b9c0d-1e2f-4c2c-d1a5-11b2c3d4e5f6';
SET @uuid_veg_stirfry       = '289c0d1e-2f3a-4d3d-e2b6-22c3d4e5f6a7';
SET @uuid_veg_root          = '390d1e2f-3a4b-4e4e-f3c7-33d4e5f6a7b8';
SET @uuid_grain_staple      = '4a1e2f3a-4b5c-4f5f-a4d8-44e5f6a7b8c9';
SET @uuid_special_medicinal = '5b2f3a4b-5c6d-4a6a-b5e9-55f6a7b8c9d0';
SET @uuid_nut_raw           = '6c3a4b5c-6d7e-4b7b-c6f0-66a7b8c9d0e1';
SET @uuid_local_represent   = '7d4b5c6d-7e8f-4c8c-d7a1-77b8c9d0e1f2';
SET @uuid_livestock_egg     = '8e5c6d7e-8f9a-4d9d-e8b2-88c9d0e1f2a3';
SET @uuid_livestock_meat    = '9f6d7e8f-9a0b-4e0e-f9c3-99d0e1f2a3b4';
SET @uuid_livestock_dairy   = 'a07e8f9a-0b1c-4f1f-a0d4-00e1f2a3b4c5';

-- Level 0
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES (
  UUID_TO_BIN(@uuid_root),
  '신선직송 농산물',
  'AGRI_DIRECT',
  NULL,
  0,
  1
);

-- Level 1
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
  (UUID_TO_BIN(@uuid_fruit), '과일', 'AGRI_FRUIT', UUID_TO_BIN(@uuid_root), 1, 1),
  (UUID_TO_BIN(@uuid_vegetable), '채소', 'AGRI_VEGETABLE', UUID_TO_BIN(@uuid_root), 1, 2),
  (UUID_TO_BIN(@uuid_grain), '곡류/잡곡', 'AGRI_GRAIN', UUID_TO_BIN(@uuid_root), 1, 3),
  (UUID_TO_BIN(@uuid_special), '버섯/특용작물', 'AGRI_SPECIAL', UUID_TO_BIN(@uuid_root), 1, 4),
  (UUID_TO_BIN(@uuid_nut), '견과류', 'AGRI_NUT', UUID_TO_BIN(@uuid_root), 1, 5),
  (UUID_TO_BIN(@uuid_local_specialty), '지역 특산물', 'AGRI_LOCAL_SPECIALTY', UUID_TO_BIN(@uuid_root), 1, 6),
  (UUID_TO_BIN(@uuid_livestock), '축산물', 'AGRI_LIVESTOCK', UUID_TO_BIN(@uuid_root), 1, 7);

-- Level 2: 과일
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
  (UUID_TO_BIN(@uuid_fruit_seasonal), '제철 과일', 'AGRI_FRUIT_SEASONAL', UUID_TO_BIN(@uuid_fruit), 2, 1),
  (UUID_TO_BIN(@uuid_fruit_storage), '저장 과일', 'AGRI_FRUIT_STORAGE', UUID_TO_BIN(@uuid_fruit), 2, 2),
  (UUID_TO_BIN(@uuid_fruit_berry), '베리류', 'AGRI_FRUIT_BERRY', UUID_TO_BIN(@uuid_fruit), 2, 3);

-- Level 2: 채소
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
  (UUID_TO_BIN(@uuid_veg_salad), '샐러드 채소', 'AGRI_VEG_SALAD', UUID_TO_BIN(@uuid_vegetable), 2, 1),
  (UUID_TO_BIN(@uuid_veg_soup), '국/찌개용 채소', 'AGRI_VEG_SOUP', UUID_TO_BIN(@uuid_vegetable), 2, 2),
  (UUID_TO_BIN(@uuid_veg_stirfry), '볶음/구이 채소', 'AGRI_VEG_STIRFRY', UUID_TO_BIN(@uuid_vegetable), 2, 3),
  (UUID_TO_BIN(@uuid_veg_root), '뿌리채소', 'AGRI_VEG_ROOT', UUID_TO_BIN(@uuid_vegetable), 2, 4);

-- Level 2: 기타
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
  (UUID_TO_BIN(@uuid_grain_staple), '쌀/주곡', 'AGRI_GRAIN_STAPLE', UUID_TO_BIN(@uuid_grain), 2, 1),
  (UUID_TO_BIN(@uuid_special_medicinal), '약용/기능성 작물', 'AGRI_SPECIAL_MEDICINAL', UUID_TO_BIN(@uuid_special), 2, 1),
  (UUID_TO_BIN(@uuid_nut_raw), '견과류 원물', 'AGRI_NUT_RAW', UUID_TO_BIN(@uuid_nut), 2, 1),
  (UUID_TO_BIN(@uuid_local_represent), '지역 대표 특산물', 'AGRI_LOCAL_REPRESENT', UUID_TO_BIN(@uuid_local_specialty), 2, 1);

-- Level 2: 축산물
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
  (UUID_TO_BIN(@uuid_livestock_egg), '계란', 'AGRI_LIVESTOCK_EGG', UUID_TO_BIN(@uuid_livestock), 2, 1),
  (UUID_TO_BIN(@uuid_livestock_meat), '육류', 'AGRI_LIVESTOCK_MEAT', UUID_TO_BIN(@uuid_livestock), 2, 2),
  (UUID_TO_BIN(@uuid_livestock_dairy), '유제품', 'AGRI_LIVESTOCK_DAIRY', UUID_TO_BIN(@uuid_livestock), 2, 3);
