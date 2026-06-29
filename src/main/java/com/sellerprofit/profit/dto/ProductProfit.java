package com.sellerprofit.profit.dto;

import java.math.BigDecimal;

/**
 * 상품 한 건의 순이익 결과 (기타비용 배분 後).
 *
 * @param revenue       정산 실수령 합 (매출 진실 원천)
 * @param units         COGS 기준 수량 = 주문수량 − 반품수량 (실제 판매로 남은 수량)
 * @param returnedUnits 반품 수량 합 (COGS 보정에 사용, 표에 함께 노출)
 * @param cogsTotal     원가 합 (units × COGS)
 * @param allocatedCost 매출 비율로 배분된 기타비용
 * @param profit        순이익 = revenue − cogsTotal − allocatedCost
 * @param marginPct     마진율(%) = profit / revenue × 100, revenue 0 이면 null
 * @param loss          적자 여부 (profit < 0) — 대시보드 하이라이트용
 */
public record ProductProfit(
        Long productId,
        String name,
        BigDecimal revenue,
        long units,
        long returnedUnits,
        BigDecimal cogsTotal,
        BigDecimal allocatedCost,
        BigDecimal profit,
        BigDecimal marginPct,
        boolean loss
) {
}
