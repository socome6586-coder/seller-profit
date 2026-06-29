package com.sellerprofit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. 성공하면 서버 세션에 userId 가 저장된다(쿠키 기반).
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
