package com.sellerprofit.manage.dto;

import com.sellerprofit.domain.Product;

import java.math.BigDecimal;

/**
 * 상품 목록/원가 입력 화면용 뷰. cogs 가 null 이면 '미입력'.
 */
public record ProductView(
        Long id,
        String vendorItemId,
        String name,
        BigDecimal cogs
) {
    public static ProductView of(Product p) {
        return new ProductView(p.getId(), p.getVendorItemId(), p.getName(), p.getCogs());
    }
}
