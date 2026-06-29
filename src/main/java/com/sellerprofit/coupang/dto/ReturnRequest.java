package com.sellerprofit.coupang.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 반품요청 1건 (응답 data[]). 한 건에 여러 상품 라인(returnItems)이 들어올 수 있다.
 *
 * ⚠️ [검증 포인트] 아래 JSON 필드명은 쿠팡 라이브 문서 기준으로 최종 확인 필요.
 *    실제 키가 다르면 @JsonProperty 매핑만 바꾸면 된다(구조는 그대로).
 *    - receiptId     : 반품 접수번호(고유) → 멱등 키(external_ref) 구성에 사용
 *    - orderId       : 원주문 번호(있으면 보관)
 *    - createdAt     : 반품 접수일시 → 접수일(requested_at)로 변환
 *    - receiptStatus : 반품 상태(접수/회수중/완료 등) 원본 보관
 *    - reason        : 반품 사유(없을 수도 있음)
 *    - returnItems[] : 반품 상품 라인
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReturnRequest(
        Long receiptId,
        Long orderId,
        String createdAt,
        String receiptStatus,
        String reason,
        List<CoupangReturnItem> returnItems
) {
}
