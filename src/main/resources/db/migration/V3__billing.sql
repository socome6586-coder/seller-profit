-- =====================================================================
-- V3 — 토스페이먼츠 빌링(정기결제) 저장 컬럼
-- =====================================================================
-- 왜 필요한가:
--   * PRO 구독은 토스 '빌링키'(카드 토큰)로 매월 자동결제한다.
--   * 빌링키는 카드에 준하는 민감정보 → API 키와 동일하게 AES-256-GCM 암호화(BYTEA) 저장.
--   * customerKey 는 토스에 우리 회원을 식별시키는 비식별 키(이메일/PII 금지) → 평문 보관.
--   * 결제 성공 시점을 기록해 다음 청구 주기/재시도 판단에 쓴다.
-- 설계 원칙은 V1/V2 와 동일: 민감정보 암호화, 멱등.
-- ※ Phase 3 스캐폴딩 단계 — 실제 토스 키는 환경변수로 주입(미설정 시 결제 호출 차단).
-- =====================================================================

ALTER TABLE users
    ADD COLUMN billing_key_encrypted BYTEA,                 -- 토스 빌링키(암호화). NULL = 미등록
    ADD COLUMN billing_customer_key  VARCHAR(64),           -- 토스 customerKey(비식별, UUID 등)
    ADD COLUMN last_billed_at        TIMESTAMPTZ;           -- 마지막 결제 성공 시각

-- 정기결제 대상(ACTIVE 이고 주기 만료가 임박/경과한 유저) 조회용.
CREATE INDEX idx_users_billing_due ON users(subscription_status, current_period_end);
