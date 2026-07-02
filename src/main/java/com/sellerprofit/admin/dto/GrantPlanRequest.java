package com.sellerprofit.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** PRO N개월 무상 지급 요청(T10.3). plan 은 현재 "PRO" 만 지원(서비스 계층에서 400 처리). */
public record GrantPlanRequest(
        @NotNull @Min(1) Integer months,
        @NotBlank String plan
) {
}
