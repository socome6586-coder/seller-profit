package com.sellerprofit.repository;

import java.math.BigDecimal;

/**
 * 네이티브 순이익 집계 쿼리의 결과 행 매핑(Spring Data Projection).
 * SELECT 별칭(alias)과 getter 이름이 일치해야 한다.
 */
public interface ProductProfitRow {
    Long getProductId();
    String getName();
    String getVendorItemId();      // SKU. 광고 ROI 집계(ads)가 ad_spends 와 매칭하는 키
    BigDecimal getPayout();        // 정산 실수령 합
    Long getUnits();               // COGS 기준 수량 = 주문수량 − 반품수량 (0 미만 0)
    Long getReturnedUnits();       // 반품수량 합
    BigDecimal getCogsTotal();     // 원가 합 (COGS기준수량 × COGS)
    BigDecimal getAdSpend();       // 이 SKU(vendor_item_id)로 귀속된 광고비 합 (기간 내)
    BigDecimal getProfit();        // 순이익 (기타비용 배분 前, 광고비 반영 前)
}
