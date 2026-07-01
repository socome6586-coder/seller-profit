-- =====================================================================
-- V4 — 광고비(ad_spends) 테이블 : 광고 ROI × 순이익 옵티마이저
-- =====================================================================
-- 왜 필요한가:
--   * 광고비를 SKU(vendor_item_id) 단위로 귀속시켜 "광고전 기여이익 vs 광고비"로
--     '광고 돌릴수록 손해인 SKU'를 적발한다(docs/ad-roi-spec.md).
--   * v1 데이터 소스 = 수기 입력 + CSV 업로드(쿠팡 광고센터 리포트). 쿠팡 광고 API 는 후속.
--   * ⚠️ 이중차감 주의: 광고비는 이제 ad_spends 로만 관리하고, 순이익 엔진의 기타비용
--     배분에서는 광고성 비용(costs.cost_type='AD')을 제외한다(불변식: 전체 순이익 합계 불변).
-- 설계 원칙은 V1/V2/V3 와 동일: 금액 NUMERIC(14,2), 멱등 수집(UNIQUE), updated_at 트리거.
-- =====================================================================

CREATE TABLE ad_spends (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    market_account_id  BIGINT NOT NULL REFERENCES market_accounts(id) ON DELETE CASCADE,
    vendor_item_id     VARCHAR(50),                        -- SKU. NULL = 미할당(캠페인 단위만 있는 spend)
    campaign           VARCHAR(255),                       -- 차원(rollup 용)
    ad_group           VARCHAR(255),                       -- 차원
    keyword            VARCHAR(255),                       -- 차원
    spend_date         DATE NOT NULL,                      -- 광고 집행일(집계 기준)
    amount             NUMERIC(14,2) NOT NULL CHECK (amount >= 0),
    source             VARCHAR(20) NOT NULL
        CHECK (source IN ('MANUAL','CSV','COUPANG_ADS')),  -- COUPANG_ADS 는 후속(Provider)
    external_ref       VARCHAR(300) NOT NULL,              -- 멱등 키(source:campaign:adGroup:keyword:vendorItemId:spendDate)
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (market_account_id, external_ref)               -- 멱등(CSV 재업로드 시 중복 차단)
);

-- 기간 조회(대시보드 from/to) 용
CREATE INDEX idx_ad_spends_account_date ON ad_spends(market_account_id, spend_date);
-- SKU 단위 귀속 집계 용
CREATE INDEX idx_ad_spends_account_sku_date ON ad_spends(market_account_id, vendor_item_id, spend_date);

-- V1 의 공용 트리거 함수 set_updated_at() 재사용(규약 trg_<table>_updated).
CREATE TRIGGER trg_ad_spends_updated
    BEFORE UPDATE ON ad_spends
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
