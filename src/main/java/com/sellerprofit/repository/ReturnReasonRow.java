package com.sellerprofit.repository;

/**
 * 반품 사유별 집계 결과 행(Spring Data Projection).
 * JPQL SELECT 별칭(alias)과 getter 이름이 일치해야 한다.
 */
public interface ReturnReasonRow {
    String getReason();        // 반품 사유(없으면 '미상')
    Long getQuantity();        // 사유별 반품 수량 합
    Long getLineCount();       // 사유별 반품 라인 수
}
