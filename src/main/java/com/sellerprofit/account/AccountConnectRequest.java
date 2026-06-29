package com.sellerprofit.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 쿠팡 계정 연동 요청. 키는 절대 로그/응답에 노출하지 않는다(저장 시 암호화).
 *
 * MVP 단일 채널(쿠팡)이라 channel 은 받지 않는다(엔티티 기본값 COUPANG).
 */
public record AccountConnectRequest(
        @NotBlank(message = "업체코드(vendorId)는 필수입니다") @Size(max = 50) String vendorId,
        @NotBlank(message = "Access Key 는 필수입니다") String accessKey,
        @NotBlank(message = "Secret Key 는 필수입니다") String secretKey) {
}
