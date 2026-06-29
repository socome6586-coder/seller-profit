package com.sellerprofit.subscription;

import com.sellerprofit.domain.type.SubscriptionStatus;

import java.util.List;

/**
 * 구독 플랜 카탈로그. 가격/한도/혜택을 코드로 고정한다(요금 페이지·게이팅 공용).
 *
 * 유저 확보가 우선이라 FREE 플랜을 항상 제공한다(가입 기본값).
 * 한도 -1 은 '무제한'을 뜻한다.
 *
 * ※ 가격(원)·한도는 비즈니스 정책값이라 바뀔 수 있다. 한 곳(여기)만 고치면 된다.
 */
public enum PlanType {

    FREE("무료", 0, 1, 30, List.of(
            "쿠팡 계정 1개 연동",
            "최근 30일 순이익 대시보드",
            "반품/사유 분석")),

    PRO("프로", 9900, -1, -1, List.of(
            "쿠팡 계정 무제한 연동",
            "기간 제한 없는 순이익 분석",
            "반품/사유 분석",
            "우선 지원"));

    private final String displayName;
    private final int monthlyPrice;            // 월 구독료(원). FREE=0
    private final int maxMarketAccounts;       // 연동 가능한 마켓 계정 수(-1 무제한)
    private final int dashboardLookbackDays;   // 대시보드 조회 가능 기간(일, -1 무제한)
    private final List<String> features;

    PlanType(String displayName, int monthlyPrice,
             int maxMarketAccounts, int dashboardLookbackDays, List<String> features) {
        this.displayName = displayName;
        this.monthlyPrice = monthlyPrice;
        this.maxMarketAccounts = maxMarketAccounts;
        this.dashboardLookbackDays = dashboardLookbackDays;
        this.features = features;
    }

    public String displayName() {
        return displayName;
    }

    public int monthlyPrice() {
        return monthlyPrice;
    }

    public int maxMarketAccounts() {
        return maxMarketAccounts;
    }

    public int dashboardLookbackDays() {
        return dashboardLookbackDays;
    }

    public List<String> features() {
        return features;
    }

    public boolean isPaid() {
        return monthlyPrice > 0;
    }

    /**
     * 구독 상태 → 적용 플랜 매핑.
     * FREE 상태면 무료 플랜, 그 외(ACTIVE/PAST_DUE/CANCELED)는 유료(PRO) 플랜으로 본다.
     * ※ CANCELED 의 '기간 만료 후 FREE 강등'은 결제 연동(Phase 3)에서 만료 시점에 상태를 바꿔 처리한다.
     */
    public static PlanType fromStatus(SubscriptionStatus status) {
        return status == SubscriptionStatus.FREE ? FREE : PRO;
    }
}
