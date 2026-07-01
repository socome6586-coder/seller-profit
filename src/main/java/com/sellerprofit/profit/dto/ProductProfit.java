package com.sellerprofit.profit.dto;

import java.math.BigDecimal;

/**
 * 상품 한 건의 순이익 결과 (기타비용 + 광고비 배분 後 = 메인 대시보드 "진짜 순이익").
 *
 * @param vendorItemId  SKU. 광고 ROI 집계(com.sellerprofit.ads)가 ad_spends 와 매칭하는 키
 * @param revenue       정산 실수령 합 (매출 진실 원천)
 * @param units         COGS 기준 수량 = 주문수량 − 반품수량 (실제 판매로 남은 수량)
 * @param returnedUnits 반품 수량 합 (COGS 보정에 사용, 표에 함께 노출)
 * @param cogsTotal     원가 합 (units × COGS)
 * @param allocatedCost 매출 비율로 배분된 기타비용 (광고성 비용 제외 — docs/DECISIONS.md D1)
 * @param preAdProfit   광고비 반영 前 순이익 = revenue − cogsTotal − allocatedCost.
 *                      "광고전 기여이익"과 동일 산식 — {@code com.sellerprofit.ads.AdRoiService} 가
 *                      이 값을 contributionProfit 으로 그대로 소비한다(그 화면은 광고전 기준 유지).
 * @param adSpend       이 SKU 로 귀속된 광고비(ad_spends, 기간 내 합)
 * @param profit        진짜 순이익(광고후) = preAdProfit − adSpend. 메인 대시보드 헤드라인·정렬·
 *                      적자 판정이 쓰는 값.
 * @param marginPct     마진율(%, 광고후 기준) = profit / revenue × 100, revenue 0 이면 null
 * @param loss          적자 여부 (profit < 0, 광고후 기준) — 대시보드 하이라이트용
 */
public record ProductProfit(
        Long productId,
        String name,
        String vendorItemId,
        BigDecimal revenue,
        long units,
        long returnedUnits,
        BigDecimal cogsTotal,
        BigDecimal allocatedCost,
        BigDecimal preAdProfit,
        BigDecimal adSpend,
        BigDecimal profit,
        BigDecimal marginPct,
        boolean loss
) {
}
