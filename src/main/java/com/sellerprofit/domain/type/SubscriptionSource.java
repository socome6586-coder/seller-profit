package com.sellerprofit.domain.type;

/**
 * 구독 취득 경로. @Enumerated(STRING) 으로 저장 → DB엔 'PAID','COMP' 그대로. 기본값 PAID.
 *
 * PAID 는 토스 빌링으로 결제된 구독, COMP 는 관리자가 무상 지급한 구독(베타테스터 등).
 * 매출 지표는 반드시 PAID 만 집계해야 하므로 절대 섞지 않는다(docs/admin-tasks.md 규칙 4).
 * 빌링 스케줄러는 COMP 구독에 결제를 시도하지 않는다(빌링키가 없음).
 */
public enum SubscriptionSource {
    PAID,
    COMP
}
