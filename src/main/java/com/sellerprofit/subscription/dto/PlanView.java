package com.sellerprofit.subscription.dto;

import com.sellerprofit.subscription.PlanType;

import java.util.List;

/**
 * 요금제 카탈로그 1건(요금 페이지 렌더용).
 *
 * @param code                  플랜 코드(FREE/PRO)
 * @param name                  표시 이름
 * @param monthlyPrice          월 구독료(원), FREE=0
 * @param paid                  유료 여부
 * @param maxMarketAccounts     연동 가능한 마켓 계정 수(-1 무제한)
 * @param dashboardLookbackDays 대시보드 조회 가능 기간(일, -1 무제한)
 * @param features              혜택 목록
 */
public record PlanView(
        String code,
        String name,
        int monthlyPrice,
        boolean paid,
        int maxMarketAccounts,
        int dashboardLookbackDays,
        List<String> features
) {
    public static PlanView of(PlanType plan) {
        return new PlanView(
                plan.name(),
                plan.displayName(),
                plan.monthlyPrice(),
                plan.isPaid(),
                plan.maxMarketAccounts(),
                plan.dashboardLookbackDays(),
                plan.features());
    }
}
