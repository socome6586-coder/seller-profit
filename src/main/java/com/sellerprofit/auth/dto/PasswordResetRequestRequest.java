package com.sellerprofit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** 비밀번호 재설정 이메일 발송 요청. */
public record PasswordResetRequestRequest(
        @NotBlank @Email String email
) {
}
