-- =====================================================================
-- 쿠팡 순이익 분석 도구 — PostgreSQL 스키마 (MVP)
-- =====================================================================
-- 설계 원칙
--   * 금액은 전부 NUMERIC(14,2). 부동소수(float/double) 절대 금지.
--   * 매출의 진실 원천 = settlements(정산 실지급액). 수수료 재계산 안 함.
--   * 외부 데이터 수집은 멱등(idempotent)하게 — UNIQUE 키로 중복 INSERT 차단.
--   * API 키는 앱 레벨에서 암호화 후 BYTEA로만 저장. 평문/로그 노출 금지.
-- =====================================================================


-- updated_at 자동 갱신 트리거 함수 (여러 테이블 공용)
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ---------------------------------------------------------------------
-- 1. users — 셀러 계정
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,            -- BCrypt 등 단방향 해시
    subscription_status  VARCHAR(20)  NOT NULL DEFAULT 'FREE'
        CHECK (subscription_status IN ('FREE','ACTIVE','PAST_DUE','CANCELED')),
    current_period_end   TIMESTAMPTZ,                      -- 유료 구독 만료 시점
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_users_updated
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------
-- 2. market_accounts — 연동 채널 (MVP: 쿠팡)
--    ⚠️ access_key / secret_key 는 앱에서 AES-GCM 암호화 후 BYTEA로 저장.
--       (iv는 ciphertext 앞에 prepend 하거나 별도 컬럼으로 관리)
-- ---------------------------------------------------------------------
CREATE TABLE market_accounts (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel                   VARCHAR(20) NOT NULL DEFAULT 'COUPANG'
        CHECK (channel IN ('COUPANG')),                   -- 다채널은 이후 확장
    vendor_id                 VARCHAR(50) NOT NULL,         -- 쿠팡 업체코드
    access_key_encrypted      BYTEA NOT NULL,
    secret_key_encrypted      BYTEA NOT NULL,
    last_order_synced_at      TIMESTAMPTZ,                  -- 증분 동기화 커서
    last_settlement_synced_at TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, channel, vendor_id)                  -- 동일 채널 중복 연동 방지
);

CREATE INDEX idx_market_accounts_user ON market_accounts(user_id);

CREATE TRIGGER trg_market_accounts_updated
    BEFORE UPDATE ON market_accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------
-- 3. products — 상품 + 매입원가(COGS, 셀러 직접 입력)
--    ※ MVP 단순화: cogs는 '현재값' 1개만 보관.
--      재입고로 원가가 바뀌면 과거 마진이 소급 변동됨(알려진 한계).
--      정확한 과거 집계가 필요해지면 product_cost_history 테이블로 확장.
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market_account_id  BIGINT NOT NULL REFERENCES market_accounts(id) ON DELETE CASCADE,
    vendor_item_id     VARCHAR(50) NOT NULL,               -- 쿠팡 옵션상품 식별자
    name               VARCHAR(500) NOT NULL,
    cogs               NUMERIC(14,2),                       -- 매입원가, NULL = 미입력
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (market_account_id, vendor_item_id)
);

CREATE INDEX idx_products_market_account ON products(market_account_id);

CREATE TRIGGER trg_products_updated
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ---------------------------------------------------------------------
-- 4. order_items — 주문(상품 라인 단위). 쿠팡 주문 1건 = 여러 라인.
--    판매 '수량' 집계 + COGS 곱셈의 근거.
-- ---------------------------------------------------------------------
CREATE TABLE order_items (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market_account_id  BIGINT NOT NULL REFERENCES market_accounts(id) ON DELETE CASCADE,
    product_id         BIGINT REFERENCES products(id) ON DELETE SET NULL,
    coupang_order_id   VARCHAR(50) NOT NULL,               -- 쿠팡 주문번호
    vendor_item_id     VARCHAR(50) NOT NULL,               -- 상품 매칭 보조
    quantity           INTEGER     NOT NULL CHECK (quantity > 0),
    sale_price         NUMERIC(14,2) NOT NULL,             -- 판매 단가(참고)
    status             VARCHAR(30) NOT NULL,               -- 쿠팡 상태값 그대로 보관
    ordered_at         TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (market_account_id, coupang_order_id, vendor_item_id)  -- 멱등(중복 수집 차단)
);

CREATE INDEX idx_order_items_account_date ON order_items(market_account_id, ordered_at);
CREATE INDEX idx_order_items_product       ON order_items(product_id);


-- ---------------------------------------------------------------------
-- 5. settlements — 정산(지급) 내역.
--    수수료 차감 후 '실지급액'이라 매출의 진실 원천. 반품 시 음수로 들어옴.
-- ---------------------------------------------------------------------
CREATE TABLE settlements (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market_account_id  BIGINT NOT NULL REFERENCES market_accounts(id) ON DELETE CASCADE,
    product_id         BIGINT REFERENCES products(id) ON DELETE SET NULL,
    vendor_item_id     VARCHAR(50) NOT NULL,
    external_ref       VARCHAR(100) NOT NULL,              -- 쿠팡 정산 식별자(멱등 키)
    payout_amount      NUMERIC(14,2) NOT NULL,             -- 실지급액(반품 음수 포함)
    fee_amount         NUMERIC(14,2),                       -- 참고용 수수료(있으면)
    settled_at         DATE NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (market_account_id, external_ref)               -- 멱등(중복 수집 차단)
);

CREATE INDEX idx_settlements_account_date ON settlements(market_account_id, settled_at);
CREATE INDEX idx_settlements_product       ON settlements(product_id);


-- ---------------------------------------------------------------------
-- 6. costs — 기타 비용(광고비/배송비 등). 기간 총액 → 앱에서 매출 비율로 배분.
-- ---------------------------------------------------------------------
CREATE TABLE costs (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    cost_type     VARCHAR(20) NOT NULL
        CHECK (cost_type IN ('AD','SHIPPING','ETC')),
    amount        NUMERIC(14,2) NOT NULL,
    period_start  DATE NOT NULL,
    period_end    DATE NOT NULL,
    memo          VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (period_end >= period_start)
);

CREATE INDEX idx_costs_user_period ON costs(user_id, period_start, period_end);

CREATE TRIGGER trg_costs_updated
    BEFORE UPDATE ON costs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- =====================================================================
-- 검증용 예시 쿼리(기간별 상품 순이익)는 여기서 실행하지 않는다.
--   * 운영 쿼리: ProductRepository.findProfitByPeriod (네이티브)
--   * 설명/주석 버전: docs/schema.sql
-- ⚠️ 함정: settlements 와 order_items 를 한 번에 JOIN 하면 fan-out 으로 SUM 이
--    부풀려진다 → 각각 CTE 로 먼저 집계 후 LEFT JOIN. (named param `:account` 등은
--    JDBC 바인딩용이라 마이그레이션에 그대로 두면 문법 에러가 난다.)
-- =====================================================================
