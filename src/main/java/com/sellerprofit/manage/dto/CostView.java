package com.sellerprofit.manage.dto;

import com.sellerprofit.domain.Cost;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기타비용 목록 화면용 뷰.
 */
public record CostView(
        Long id,
        String costType,
        BigDecimal amount,
        LocalDate periodStart,
        LocalDate periodEnd,
        String memo
) {
    public static CostView of(Cost c) {
        return new CostView(c.getId(), c.getCostType().name(), c.getAmount(),
                c.getPeriodStart(), c.getPeriodEnd(), c.getMemo());
    }
}
