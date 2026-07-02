package com.sellerprofit.admin;

import com.sellerprofit.domain.type.SubscriptionStatus;
import com.sellerprofit.subscription.SubscriptionService;
import com.sellerprofit.subscription.dto.CompGrantResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PRO N개월 무상 지급(T10.3) + 회수(T10.4, 선택) 오케스트레이션. 도메인 우회 금지 — 실제
 * 지급/회수 로직은 {@link SubscriptionService} 를 통해서만 처리하고, 여기서는 입력검증 +
 * 감사 로그 기록만 담당한다.
 */
@Service
public class AdminGrantService {

    private final SubscriptionService subscriptionService;
    private final AdminAuditService adminAuditService;

    public AdminGrantService(SubscriptionService subscriptionService, AdminAuditService adminAuditService) {
        this.subscriptionService = subscriptionService;
        this.adminAuditService = adminAuditService;
    }

    /**
     * @param adminUserId  지급을 수행하는 관리자
     * @param targetUserId 지급 대상 유저
     * @param months       지급 개월(1 이상, 그 외 400)
     * @param plan         현재는 "PRO" 만 지원(그 외 400)
     */
    @Transactional
    public void grantPro(Long adminUserId, Long targetUserId, Integer months, String plan) {
        if (months == null || months <= 0) {
            throw new IllegalArgumentException("months 는 1 이상의 정수여야 합니다.");
        }
        if (!"PRO".equals(plan)) {
            throw new IllegalArgumentException("plan 은 PRO 만 지원합니다.");
        }

        CompGrantResult result = subscriptionService.grantComp(targetUserId, months);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("months", months);
        detail.put("plan", plan);
        detail.put("before", result.before() == null ? null : result.before().toString());
        detail.put("after", result.after().toString());
        adminAuditService.record(adminUserId, "GRANT_PLAN", targetUserId, detail);
    }

    /**
     * COMP 회수(선택). PAID 구독은 대상이 아니다(400) — {@link SubscriptionService#revokeComp} 참고.
     *
     * @param adminUserId  회수를 수행하는 관리자
     * @param targetUserId 회수 대상 유저
     */
    @Transactional
    public void revokePro(Long adminUserId, Long targetUserId) {
        SubscriptionStatus before = subscriptionService.revokeComp(targetUserId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before.name());
        detail.put("after", SubscriptionStatus.FREE.name());
        adminAuditService.record(adminUserId, "REVOKE_PLAN", targetUserId, detail);
    }
}
