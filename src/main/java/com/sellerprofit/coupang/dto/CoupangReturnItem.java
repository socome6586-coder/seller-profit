package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 반품 상품 라인 (반품요청의 returnItems[]).
 *
 * ⚠️ [검증 포인트] 아래 JSON 필드명은 쿠팡 라이브 문서 기준으로 최종 확인 필요.
 *    - vendorItemId   : 옵션상품 식별자(숫자) → 우리 상품 매칭 키
 *    - vendorItemName : 상품명(없을 수도 있음)
 *    - purchaseCount  : 반품 수량
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoupangReturnItem(
        Long vendorItemId,
        String vendorItemName,
        Integer purchaseCount
) {
}
