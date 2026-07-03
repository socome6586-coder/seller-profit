package com.sellerprofit.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 가입 즉시 무료(FREE) 플랜으로 시작한다.
 *
 * @param password 평문 비밀번호(서버에서 BCrypt 해싱). BCrypt 한도상 최대 72바이트.
 *                 영문 + (숫자 또는 특수문자) 조합을 강제한다(요즘 방식 비밀번호 정책).
 * @param phone    휴대전화번호. 하이픈 포함/미포함 모두 허용하고 서비스 레벨에서 숫자만 정규화해
 *                 저장·중복확인한다(중복가입 방지).
 */
public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*[0-9!@#$%^&*()_\\-+=\\[\\]{};:'\",.<>/?]).+$",
                message = "영문과 함께 숫자 또는 특수문자를 포함해야 합니다."
        )
        String password,
        @NotBlank
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "휴대전화번호 형식이 올바르지 않습니다.")
        String phone
) {
}
