package com.sellerprofit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 가입 즉시 무료(FREE) 플랜으로 시작한다.
 *
 * @param password 평문 비밀번호(서버에서 BCrypt 해싱). BCrypt 한도상 최대 72바이트.
 */
public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
