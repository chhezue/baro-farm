-- ============================================================
-- 카테고리 초기화 SQL 스크립트
-- 농가 직송 마켓 기준 카테고리 구조 (Level 0 ~ 2)
-- ============================================================
--
-- ⚠️ 기존 카테고리 데이터 삭제 (외래 키 제약 조건 해결)
-- ============================================================

-- 외래 키 체크 일시적으로 비활성화
USE `baro_shopping`;

SET FOREIGN_KEY_CHECKS = 0;



-- 기존 카테고리 데이터 모두 삭제 (Level 2 → Level 1 → Level 0 순서)
DELETE FROM categories WHERE level = 2;
DELETE FROM categories WHERE level = 1;
DELETE FROM categories WHERE level = 0;

-- 외래 키 체크 다시 활성화
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 카테고리 데이터 삽입
-- ============================================================
--
-- ✅ 카테고리 구조 요약
--
-- Level 0: 농가직송 농산물 (루트)
--
-- Level 1 (구매 관점 대분류):
--   - 과일
--   - 채소
--   - 곡물·잡곡
--   - 버섯·특용작물
--   - 견과·건과
--   - 산지 특산물
--   - 축산물
--
-- Level 2 (사용/조리/소비 맥락):
--   - 과일: 제철 과일, 저장 과일, 베리류
--   - 채소: 쌈·샐러드 채소, 국·찌개 채소, 볶음·구이 채소, 뿌리채소
--   - 축산물: 계란, 육류, 유제품
--   - 기타: 각 대분류별 기본 하위 카테고리
--
-- ⚠️ 실행 순서 중요: 1 → 2 → 3
--
-- ⚠️ 운영 규칙:
--   - 상품은 반드시 level=2 카테고리에만 연결
--   - level=0~1은 직접 상품 매핑 금지
--   - code는 절대 변경하지 않음 (name만 변경 가능)
--
-- ✅ 고정 UUID: 스크립트 재실행 시에도 동일한 id 유지 (더미/테스트 데이터 참조용)
--
-- ============================================================

-- ============================================================
-- 고정 UUID 상수 (재실행 시에도 동일)
-- ============================================================
SET @uuid_root              = '550e8400-e29b-41d4-a716-446655440000';
SET @uuid_fruit             = '6ba7b810-9dad-41d1-80b4-00c04fd430c8';
SET @uuid_vegetable         = '7c8d9e0f-1a2b-42e2-91c5-11d2e3f4a5b6';
SET @uuid_grain             = '8d9e0f1a-2b3c-43f3-a2d6-22e3f4a5b6c7';
SET @uuid_special           = '9e0f1a2b-3c4d-44a4-b3e7-33f4a5b6c7d8';
SET @uuid_nut               = 'a01a2b3c-4d5e-45b5-c4f8-44a5b6c7d8e9';
SET @uuid_local_specialty   = 'b12b3c4d-5e6f-46c6-d5a9-55b6c7d8e9f0';
SET @uuid_livestock         = 'c23c4d5e-6f7a-47d7-e6b0-66c7d8e9f0a1';
-- Level 2
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
SET @uuid_local_represent  = '7d4b5c6d-7e8f-4c8c-d7a1-77b8c9d0e1f2';
SET @uuid_livestock_egg     = '8e5c6d7e-8f9a-4d9d-e8b2-88c9d0e1f2a3';
SET @uuid_livestock_meat    = '9f6d7e8f-9a0b-4e0e-f9c3-99d0e1f2a3b4';
SET @uuid_livestock_dairy   = 'a07e8f9a-0b1c-4f1f-a0d4-00e1f2a3b4c5';

-- ============================================================
-- 1️⃣ Level 0 — 루트
-- ============================================================

INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES (
           uuid_to_bin(@uuid_root),
           '농가직송 농산물',
           'AGRI_DIRECT',
           NULL,
           0,
           1
       );

-- ============================================================
-- 2️⃣ Level 1 — 대분류 (구매 기준)
-- ============================================================

INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
    (uuid_to_bin(@uuid_fruit), '과일', 'AGRI_FRUIT', uuid_to_bin(@uuid_root), 1, 1),
    (uuid_to_bin(@uuid_vegetable), '채소', 'AGRI_VEGETABLE', uuid_to_bin(@uuid_root), 1, 2),
    (uuid_to_bin(@uuid_grain), '곡물·잡곡', 'AGRI_GRAIN', uuid_to_bin(@uuid_root), 1, 3),
    (uuid_to_bin(@uuid_special), '버섯·특용작물', 'AGRI_SPECIAL', uuid_to_bin(@uuid_root), 1, 4),
    (uuid_to_bin(@uuid_nut), '견과·건과', 'AGRI_NUT', uuid_to_bin(@uuid_root), 1, 5),
    (uuid_to_bin(@uuid_local_specialty), '산지 특산물', 'AGRI_LOCAL_SPECIALTY', uuid_to_bin(@uuid_root), 1, 6),
    (uuid_to_bin(@uuid_livestock), '축산물', 'AGRI_LIVESTOCK', uuid_to_bin(@uuid_root), 1, 7);

-- ============================================================
-- 3️⃣ Level 2 — 사용 / 소비 맥락 기준
-- ============================================================

-- 🍎 Level 2: 과일 하위
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
    (uuid_to_bin(@uuid_fruit_seasonal), '제철 과일', 'AGRI_FRUIT_SEASONAL', uuid_to_bin(@uuid_fruit), 2, 1),
    (uuid_to_bin(@uuid_fruit_storage), '저장 과일', 'AGRI_FRUIT_STORAGE', uuid_to_bin(@uuid_fruit), 2, 2),
    (uuid_to_bin(@uuid_fruit_berry), '베리류', 'AGRI_FRUIT_BERRY', uuid_to_bin(@uuid_fruit), 2, 3);

-- 🥬 Level 2: 채소 하위
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
    (uuid_to_bin(@uuid_veg_salad), '쌈·샐러드 채소', 'AGRI_VEG_SALAD', uuid_to_bin(@uuid_vegetable), 2, 1),
    (uuid_to_bin(@uuid_veg_soup), '국·찌개 채소', 'AGRI_VEG_SOUP', uuid_to_bin(@uuid_vegetable), 2, 2),
    (uuid_to_bin(@uuid_veg_stirfry), '볶음·구이 채소', 'AGRI_VEG_STIRFRY', uuid_to_bin(@uuid_vegetable), 2, 3),
    (uuid_to_bin(@uuid_veg_root), '뿌리채소', 'AGRI_VEG_ROOT', uuid_to_bin(@uuid_vegetable), 2, 4);

-- 🌾 Level 2: 기타 대분류 기본 하위
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
    (uuid_to_bin(@uuid_grain_staple), '식사용 곡물', 'AGRI_GRAIN_STAPLE', uuid_to_bin(@uuid_grain), 2, 1),
    (uuid_to_bin(@uuid_special_medicinal), '약용·산지 작물', 'AGRI_SPECIAL_MEDICINAL', uuid_to_bin(@uuid_special), 2, 1),
    (uuid_to_bin(@uuid_nut_raw), '견과류', 'AGRI_NUT_RAW', uuid_to_bin(@uuid_nut), 2, 1),
    (uuid_to_bin(@uuid_local_represent), '지역 대표 농산물', 'AGRI_LOCAL_REPRESENT', uuid_to_bin(@uuid_local_specialty), 2, 1);

-- 🥩 Level 2: 축산물 하위
INSERT INTO categories (id, name, code, parent_id, level, sort_order)
VALUES
    (uuid_to_bin(@uuid_livestock_egg), '계란', 'AGRI_LIVESTOCK_EGG', uuid_to_bin(@uuid_livestock), 2, 1),
    (uuid_to_bin(@uuid_livestock_meat), '육류', 'AGRI_LIVESTOCK_MEAT', uuid_to_bin(@uuid_livestock), 2, 2),
    (uuid_to_bin(@uuid_livestock_dairy), '유제품', 'AGRI_LIVESTOCK_DAIRY', uuid_to_bin(@uuid_livestock), 2, 3);

-- ============================================================
-- 구조의 장점
-- ============================================================
-- ✅ level 2까지만으로도 UX / 필터 / AI 충분
-- ✅ 상품은 level=2에 바로 매핑 가능
-- ✅ 나중에 필요하면 level 3(품목 세분화) 자연스럽게 추가 가능
-- ✅ 기타 같은 도메인 부채 없음
-- ✅ AI 프롬프트에서 의미가 명확함
-- ============================================================
