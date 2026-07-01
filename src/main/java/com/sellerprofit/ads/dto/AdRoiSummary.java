package com.sellerprofit.ads.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 광고 ROI 대시보드 응답(docs/ad-roi-spec.md §8) — 헤드라인 + SKU 표(광고손실 SKU 최상단 정렬).
 *
 * @param totalAdSpend      기간 내 광고비 총액(미할당 포함)
 * @param reviewAdSpend     "재검토 대상 광고비" = 광고손실 SKU들의 광고비 합
 * @param unassignedAdSpend SKU 매칭 실패한 광고비 합(투명하게 별도 표기, 숨기지 않음)
 */
public record AdRoiSummary(
        LocalDate from,
        LocalDate to,
        BigDecimal totalAdSpend,
        BigDecimal reviewAdSpend,
        BigDecimal unassignedAdSpend,
        List<AdRoiRow> rows
) {
}
