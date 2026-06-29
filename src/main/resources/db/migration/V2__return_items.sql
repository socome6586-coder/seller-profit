-- =====================================================================
-- V2 — 반품/취소 수집 테이블
-- =====================================================================
-- 왜 필요한가:
--   * 매출(정산, settlements)은 반품을 음수 payout 으로 이미 반영한다.
--   * 그러나 판매수량(order_items.quantity)에는 반품이 빠지지 않아,
--     COGS(수량 × 원가)가 과대 계상된다 → 순이익이 실제보다 낮게/적자로 잘못 표시.
--   * 반품 수량을 따로 수집해 'COGS 기준 수량 = 주문수량 − 반품수량' 으로 보정한다.
--   * 발주서 목록 API 로는 완료 반품을 조회할 수 없어 별도 '반품요청 목록' API 가 필요.
-- 설계 원칙은 V1 과 동일: 금액/수량 정확, 멱등 수집(UNIQUE).
-- =====================================================================

CREATE TABLE return_items (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market_account_id  BIGINT NOT NULL REFERENCES market_accounts(id) ON DELETE CASCADE,
    product_id         BIGINT REFERENCES products(id) ON DELETE SET NULL,
    coupang_order_id   VARCHAR(50),                       -- 원주문 번호(있으면 보관, 매칭 보조)
    vendor_item_id     VARCHAR(50) NOT NULL,              -- 상품 매칭 키
    external_ref       VARCHAR(100) NOT NULL,             -- 쿠팡 반품 식별자(접수번호 등) — 멱등 키
    quantity           INTEGER NOT NULL CHECK (quantity > 0),
    reason             VARCHAR(255),                       -- 반품 사유(참고)
    status             VARCHAR(30) NOT NULL,               -- 쿠팡 반품 상태값 원본 보관
    requested_at       DATE NOT NULL,                      -- 반품 접수일(집계 기준)
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (market_account_id, external_ref)               -- 멱등(중복 수집 차단)
);

CREATE INDEX idx_return_items_account_date ON return_items(market_account_id, requested_at);
CREATE INDEX idx_return_items_product       ON return_items(product_id);

-- 증분 동기화 커서(주문/정산과 동일 패턴).
ALTER TABLE market_accounts ADD COLUMN last_return_synced_at TIMESTAMPTZ;
