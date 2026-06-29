package com.sellerprofit.profit.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 대시보드 응답: 기간 내 반품 사유별 분포.
 *
 * @param totalQuantity 기간 총 반품 수량
 * @param reasons       사유별 통계(수량 많은 순)
 */
public record ReturnReasonSummary(
        LocalDate from,
        LocalDate to,
        long totalQuantity,
        List<ReturnReasonStat> reasons
) {
}
