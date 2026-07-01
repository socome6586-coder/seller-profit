package com.sellerprofit.ads.domain;

import java.math.BigDecimal;

/**
 * 기간 내 vendor_item_id(SKU) 별 광고비 합계 프로젝션(광고 ROI 집계용).
 * {@code vendorItemId} 가 null 이면 캠페인 단위만 있는 미할당 spend 의 합.
 */
public interface AdSpendVendorItemAggregate {
    String getVendorItemId();
    BigDecimal getTotalAmount();
}
