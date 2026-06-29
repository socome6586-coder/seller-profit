package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 반품요청 목록 조회 응답 봉투.
 *
 * nextToken 이 비어있지 않으면 다음 페이지가 존재한다 → 페이징 계속.
 * ([검증 포인트] 페이징 토큰 키 이름이 'nextToken' 인지 쿠팡 문서로 확인.)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReturnRequestResponse(
        Integer code,
        String message,
        List<ReturnRequest> data,
        String nextToken
) {
}
