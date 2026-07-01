package com.sellerprofit.ads.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 광고비 수기 입력 1건.
 *
 * <p>vendorItemId 는 선택(비우면 '미할당' spend = 캠페인 단위만 있는 광고비). 차원(campaign/adGroup/keyword)도 선택.
 * amount 는 0 이상(스키마 CHECK 와 동일). 소스는 서버가 MANUAL 로 고정한다.</p>
 */
public record AdSpendRequest(
        @NotNull Long accountId,
        @Size(max = 50) String vendorItemId,
        @Size(max = 255) String campaign,
        @Size(max = 255) String adGroup,
        @Size(max = 255) String keyword,
        @NotNull LocalDate spendDate,
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal amount
) {
}
