package com.sellerprofit.subscription.dto;

import java.time.OffsetDateTime;

/**
 * 한 유저의 현재 구독 상태.
 *
 * @param status           구독 상태(FREE/ACTIVE/PAST_DUE/CANCELED)
 * @param currentPeriodEnd 유료 구독 만료 시점(무료면 null)
 * @param plan             적용 중인 플랜 상세(한도·혜택)
 */
public record SubscriptionView(
        String status,
        OffsetDateTime currentPeriodEnd,
        PlanView plan
) {
}
