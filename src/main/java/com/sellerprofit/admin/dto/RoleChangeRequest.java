package com.sellerprofit.admin.dto;

import jakarta.validation.constraints.NotBlank;

/** role 변경 요청(T10.4). 값은 "USER" | "ADMIN"(대소문자 무시, 그 외 400). */
public record RoleChangeRequest(
        @NotBlank String role
) {
}
