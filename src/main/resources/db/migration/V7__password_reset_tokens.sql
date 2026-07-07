-- =====================================================================
-- password_reset_tokens — 비밀번호 재설정 1회용 토큰
-- =====================================================================
-- 토큰 자체는 추측 불가능한 무작위 문자열(서비스 레벨에서 SecureRandom 생성)이라
-- 평문 저장해도 안전(비밀번호처럼 해싱할 필요 없음 — 발급 즉시 이메일로만 전달되고
-- DB 유출 시나리오에서도 만료시간(30분)이 노출 창을 제한한다).
CREATE TABLE password_reset_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,                -- NULL = 아직 미사용
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens(user_id);
