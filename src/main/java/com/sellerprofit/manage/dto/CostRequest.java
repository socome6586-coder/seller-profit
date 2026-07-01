package com.sellerprofit.manage.dto;

import com.sellerprofit.domain.type.CostType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기타비용(배송비/기타 등) 입력 요청.
 *
 * accountId 로 소유 user 를 찾아 비용을 귀속시킨다(비용은 user 단위 집계).
 * periodStart ~ periodEnd 기간 총액을 앱이 매출 비율로 상품에 배분한다.
 *
 * ⚠️ costType=AD 는 거부된다(docs/DECISIONS.md). 광고비는 SKU 귀속을 위해
 *    {@code /api/ads/spends}(수기) 또는 {@code /api/ads/spends/import}(CSV)로 입력한다.
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
