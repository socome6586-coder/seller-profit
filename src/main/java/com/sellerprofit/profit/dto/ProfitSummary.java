package com.sellerprofit.profit.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 대시보드 응답: 기간 합계 + 상품별 순이익 표(적자 상품이 위로 정렬, 광고후 기준).
 *
 * @param totalRevenue        총 매출(정산 실수령 합)
 * @param totalCogs           총 원가
 * @param totalAllocatedCost  기간에 배분된 기타비용 합
 * @param totalAdSpend        기간 내 이 계정의 광고비 총액(vendor_item_id 매칭 여부 무관 전체 합)
 * @param unallocatedAdSpend  totalAdSpend 중 상품 SKU 로 귀속되지 못한 몫
 *                            (vendor_item_id NULL 인 캠페인단위 spend + 이 계정 어떤 상품과도
 *                            매칭 안 되는 vendor_item_id). 실비용이므로 총순이익에서도 차감한다.
 * @param totalProfit         총 순이익(진짜) = Σ(상품별 광고후 순이익) − unallocatedAdSpend
 * @param avgMarginPct        평균 마진율(%, 광고후 기준), 매출 0 이면 null
 * @param totalReturnedUnits  총 반품 수량
 */
public record ProfitSummary(
        LocalDate from,
        LocalDate to,
        BigDecimal totalRevenue,
        BigDecimal totalCogs,
        BigDecimal totalAllocatedCost,
        BigDecimal totalAdSpend,
        BigDecimal unallocatedAdSpend,
        BigDecimal totalProfit,
        BigDecimal avgMarginPct,
        long totalReturnedUnits,
        List<ProductProfit> products
) {
}
