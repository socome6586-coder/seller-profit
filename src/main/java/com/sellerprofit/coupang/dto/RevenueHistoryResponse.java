package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 매출(정산) 내역 조회 응답 봉투.
 *
 * nextToken 이 비어있지 않으면 다음 페이지가 존재한다 → 페이징 계속(다음 요청의 token 쿼리로 전달).
 * data[] 는 주문 묶음, 각 묶음 안의 items[] 가 옵션상품별 정산 라인이다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RevenueHistoryResponse(
        Integer code,
        String message,
        List<RevenueHistory> data,
        String nextToken
) {
}
