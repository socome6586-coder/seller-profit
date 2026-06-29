package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 발주서 안의 주문 라인 한 건 (쿠팡 v4 응답의 orderItems[]).
 *
 * 매핑: vendorItemId → vendor_item_id(String), vendorItemName → products.name,
 *       shippingCount → quantity, salesPrice(단가) → sale_price.
 * salesPrice 는 단가, orderPrice 는 합계임에 주의.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoupangOrderItem(
        Long vendorItemId,
        String vendorItemName,
        Integer shippingCount,
        BigDecimal salesPrice,
        BigDecimal orderPrice
) {
}
