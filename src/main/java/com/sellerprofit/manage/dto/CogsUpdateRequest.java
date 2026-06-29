package com.sellerprofit.manage.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * 상품 매입원가(COGS) 입력/수정 요청.
 * 금액은 NUMERIC(14,2) 컬럼에 맞춰 정수 12자리·소수 2자리까지 허용한다.
 */
public record CogsUpdateRequest(
        @NotNull @PositiveOrZero @Digits(integer = 12, fraction = 2) BigDecimal cogs
) {
}
