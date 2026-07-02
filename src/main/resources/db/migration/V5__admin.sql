-- =====================================================================
-- V5 — 관리자 기능(Role + 회원 등급/플랜 관리) 저장 컬럼/테이블
-- =====================================================================
-- 왜 필요한가:
--   * 베타테스터에게 PRO 를 무상(COMP)으로 지급하려면 관리자 role 과, 지급분을
--     결제(PAID)와 구분할 source 가 필요하다(docs/admin-tasks.md 절대 규칙 4).
--   * 관리자 조작은 append-only 감사 로그로 남긴다(누가/언제/누구에게/무엇을).
-- 설계 원칙은 V1~V4 와 동일: additive(ALTER 로 컬럼 추가), 기본값으로 기존 행 안전.
-- =====================================================================

ALTER TABLE users
    ADD COLUMN role   VARCHAR(20) NOT NULL DEFAULT 'USER',   -- USER | ADMIN
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'PAID';   -- PAID | COMP (무상 지급 구분)

CREATE TABLE admin_audit (
    id              BIGSERIAL PRIMARY KEY,
    admin_user_id   BIGINT NOT NULL REFERENCES users(id),
    action          VARCHAR(30) NOT NULL,   -- GRANT_PLAN | CHANGE_ROLE | REVOKE_PLAN
    target_user_id  BIGINT NOT NULL REFERENCES users(id),
    detail          JSONB,                  -- {months, plan, before, after} 등
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_target ON admin_audit(target_user_id);
