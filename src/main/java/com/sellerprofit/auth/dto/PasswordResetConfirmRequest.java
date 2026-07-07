package com.sellerprofit.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 재설정 확정 요청. 비밀번호 정책은 회원가입({@code SignupRequest})과 동일하게
 * 영문 + (숫자 또는 특수문자) 조합을 강제한다.
 */
public record PasswordResetConfirmRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*[0-9!@#$%^&*()_\\-+=\\[\\]{};:'\",.<>/?]).+$",
                message = "영문과 함께 숫자 또는 특수문자를 포함해야 합니다."
        )
        String newPassword
) {
}
