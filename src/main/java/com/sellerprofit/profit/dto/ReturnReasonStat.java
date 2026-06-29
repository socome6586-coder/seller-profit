package com.sellerprofit.profit.dto;

import java.math.BigDecimal;

/**
 * 반품 사유 1건의 통계.
 *
 * @param reason    반품 사유(없으면 '미상')
 * @param quantity  사유별 반품 수량 합
 * @param lineCount 사유별 반품 라인 수
 * @param sharePct  전체 반품 수량 대비 비중(%)
 */
public record ReturnReasonStat(
        String reason,
        long quantity,
        long lineCount,
        BigDecimal sharePct
) {
}
