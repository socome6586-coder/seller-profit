package com.sellerprofit.domain.type;

/** 구독 상태. @Enumerated(STRING) 으로 저장 → DB엔 'FREE','ACTIVE'... 그대로. */
public enum SubscriptionStatus {
    FREE,
    ACTIVE,
    PAST_DUE,
    CANCELED
}
