package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 매출(정산) 내역 한 묶음 — 쿠팡 '매출내역 조회' 응답의 data[] 한 칸.
 *
 * 한 주문(orderId)·판매유형(saleType)·인식일(recognitionDate) 아래에 옵션상품별 정산 라인(items[])이 달린다.
 *  - saleType        : 판매유형(정상 판매/REFUND 등) — 멱등 키·반품 음수 판단에 사용
 *  - recognitionDate : 인식(정산)일자 yyyy-MM-dd — settledAt 으로 사용
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RevenueHistory(
        Long orderId,
        String saleType,
        String recognitionDate,
        String settlementDate,
        List<RevenueHistoryItem> items
) {
}
