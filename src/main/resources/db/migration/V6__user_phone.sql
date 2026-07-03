-- =====================================================================
-- users.phone — 휴대전화번호(가입 시 중복가입 방지용 고유값)
-- =====================================================================
-- 기존 유저(마이그레이션 이전 가입자)는 값이 없을 수 있어 컬럼 자체는 NULL 허용으로 둔다.
-- UNIQUE INDEX 는 Postgres 기본 동작상 NULL 여러 개를 서로 다른 값으로 취급해 충돌시키지
-- 않으므로, 기존 NULL 로우와 무관하게 신규 가입자 간 중복만 정확히 막는다.
-- "필수 입력"은 애플리케이션 레벨(SignupRequest @NotBlank)에서 강제한다.
ALTER TABLE users ADD COLUMN phone VARCHAR(20);

CREATE UNIQUE INDEX idx_users_phone ON users(phone);
