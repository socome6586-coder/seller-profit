package com.sellerprofit.ads.dto;

import java.math.BigDecimal;

/**
 * SKU 한 건의 광고 효율 (docs/ad-roi-spec.md §8).
 *
 * @param vendorItemId       SKU. products 의 upsert 키와 동일
 * @param contributionProfit 광고전 기여이익 = 정산 실수령 − COGS − (광고 제외) 배분된 기타비용
 *                            ({@link com.sellerprofit.profit.dto.ProductProfit#preAdProfit()} 와 동일 산식)
 * @param adSpend             해당 SKU·기간에 직접 귀속된 광고비 합
 * @param postAdProfit        광고후 순이익 = contributionProfit − adSpend
 * @param roas                광고비 대비 귀속 매출(참고 지표). 광고비 0 이면 null(0으로 나눌 수 없음)
 * @param adLoss              광고손실 여부(adSpend > contributionProfit) — 화면 최상단·빨강 표시 대상
 */
public record AdRoiRow(
        Long productId,
        String name,
        String vendorItemId,
        BigDecimal revenue,
        BigDecimal contributionProfit,
        BigDecimal adSpend,
        BigDecimal postAdProfit,
        BigDecimal roas,
        boolean adLoss
) {
}
