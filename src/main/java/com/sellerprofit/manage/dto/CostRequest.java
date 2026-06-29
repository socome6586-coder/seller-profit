package com.sellerprofit.manage.dto;

import com.sellerprofit.domain.type.CostType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기타비용(광고비/배송비 등) 입력 요청.
 *
 * accountId 로 소유 user 를 찾아 비용을 귀속시킨다(비용은 user 단위 집계).
 * periodStart ~ periodEnd 기간 총액을 앱이 매출 비율로 상품에 배분한다.
 */
public record CostRequest(
        @NotNull Long accountId,
        @NotNull CostType costType,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal amount,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @Size(max = 255) String memo
) {
}
