package com.sellerprofit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청. 성공하면 서버 세션에 userId 가 저장된다(쿠키 기반).
 *
 * @param remember 자동로그인("로그인 상태 유지"). true 면 세션/쿠키 수명을 30일로 늘린다
 *                 (AuthController 참고). 기본값 false — JSON 에 없으면 브라우저 세션 쿠키로만 동작.
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        boolean remember
) {
}
