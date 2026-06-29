package com.sellerprofit.repository;

import java.math.BigDecimal;

/**
 * 네이티브 순이익 집계 쿼리의 결과 행 매핑(Spring Data Projection).
 * SELECT 별칭(alias)과 getter 이름이 일치해야 한다.
 */
public interface ProductProfitRow {
    Long getProductId();
    String getName();
    BigDecimal getPayout();     // 정산 실수령 합
    Long getUnits();            // 판매수량 합
    BigDecimal getCogsTotal();  // 원가 합 (수량 × COGS)
    BigDecimal getProfit();     // 순이익 (기타비용 배분 前)
}
