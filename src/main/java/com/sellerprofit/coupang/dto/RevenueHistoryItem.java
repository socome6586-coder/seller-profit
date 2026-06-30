package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 매출(정산) 라인 1건 — 쿠팡 '매출내역 조회' 응답의 data[].items[] 한 칸.
 *
 * 인식일자/판매유형(saleType)은 부모({@link RevenueHistory})에 있고, 여기엔 옵션상품·금액만 있다.
 *  - vendorItemId  : 옵션상품 식별자(숫자)
 *  - vendorItemName: 상품명(없을 수 있음)
 *  - salePrice     : 단가
 *  - quantity      : 정산 수량
 *  - saleAmount    : 판매금액(수수료 차감 前)
 *  - serviceFee    : 판매수수료(양수, 차감 대상)
 *  - serviceFeeVat : 판매수수료 부가세(차감 대상)
 *
 * 실지급액(payout) = saleAmount − serviceFee − serviceFeeVat.
 * 반품/취소(saleType=REFUND 등)는 쿠팡이 금액을 음수로 내려주므로 자연히 차감된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RevenueHistoryItem(
        Long vendorItemId,
        String vendorItemName,
        BigDecimal salePrice,
        Integer quantity,
        BigDecimal saleAmount,
        BigDecimal serviceFee,
        BigDecimal serviceFeeVat
) {
}
