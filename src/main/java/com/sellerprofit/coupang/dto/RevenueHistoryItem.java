package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 매출(정산) 내역 한 건 (쿠팡 '매출내역 조회' 응답의 data[]).
 *
 * ⚠️ [검증 포인트] 아래 JSON 필드명은 쿠팡 라이브 문서 기준으로 최종 확인 필요.
 *    실제 응답 키가 다르면 @JsonProperty 로 매핑만 바꾸면 된다(구조는 그대로).
 *    - recognitionDate : 인식(정산)일자 yyyy-MM-dd
 *    - vendorItemId    : 옵션상품 식별자(숫자)
 *    - vendorItemName  : 상품명 (없을 수도 있음)
 *    - quantity        : 정산 수량
 *    - saleAmount      : 판매금액(수수료 차감 前)
 *    - serviceFee      : 판매수수료 (양수, 차감 대상)
 *    - settlementType  : 정산 유형(정상/반품/취소 등) — externalRef 구성·반품 음수 판단에 사용
 *
 * 실지급액(payout) = saleAmount - serviceFee 로 본다(spec 4장: 수수료 차감 후 실수령).
 * 반품/취소는 쿠팡이 음수로 내려주므로 자연히 차감된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RevenueHistoryItem(
        String recognitionDate,
        Long vendorItemId,
        String vendorItemName,
        Integer quantity,
        BigDecimal saleAmount,
        BigDecimal serviceFee,
        String settlementType
) {
}
