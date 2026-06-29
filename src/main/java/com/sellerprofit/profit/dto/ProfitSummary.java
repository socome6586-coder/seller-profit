package com.sellerprofit.profit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 대시보드 응답: 기간 합계 + 상품별 순이익 표(적자 상품이 위로 정렬).
 *
 * @param totalRevenue       총 매출(정산 실수령 합)
 * @param totalCogs          총 원가
 * @param totalAllocatedCost 기간에 배분된 기타비용 합
 * @param totalProfit        총 순이익
 * @param avgMarginPct       평균 마진율(%), 매출 0 이면 null
 * @param totalReturnedUnits 총 반품 수량
 */
public record ProfitSummary(
        LocalDate from,
        LocalDate to,
        BigDecimal totalRevenue,
        BigDecimal totalCogs,
        BigDecimal totalAllocatedCost,
        BigDecimal totalProfit,
        BigDecimal avgMarginPct,
        long totalReturnedUnits,
        List<ProductProfit> products
) {
}
