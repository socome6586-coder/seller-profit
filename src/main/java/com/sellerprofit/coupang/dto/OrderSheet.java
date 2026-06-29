package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 발주서 한 건 (쿠팡 v4 응답의 data[]).
 *
 * orderedAt 은 존 정보 없는 "2024-04-08T22:54:46" 형태 → KST(Asia/Seoul)로 간주해 변환한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSheet(
        Long orderId,
        String orderedAt,
        String status,
        List<CoupangOrderItem> orderItems
) {
}
