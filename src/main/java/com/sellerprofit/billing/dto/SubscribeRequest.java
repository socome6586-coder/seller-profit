package com.sellerprofit.billing.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 구독 시작 요청. authKey 는 프런트가 토스 SDK 로 카드를 등록한 뒤 받은 1회용 인증키.
 * (카드번호 등 민감정보는 서버로 오지 않는다 — 토스가 토큰화)
 */
public record SubscribeRequest(
        @NotBlank String authKey
) {
}
