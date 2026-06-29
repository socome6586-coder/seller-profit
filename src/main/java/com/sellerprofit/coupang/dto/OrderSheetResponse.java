package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 발주서 목록 조회 응답 봉투.
 *
 * nextToken 이 비어있지 않으면 다음 페이지가 존재한다 → 페이징 계속.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSheetResponse(
        Integer code,
        String message,
        List<OrderSheet> data,
        String nextToken
) {
}
