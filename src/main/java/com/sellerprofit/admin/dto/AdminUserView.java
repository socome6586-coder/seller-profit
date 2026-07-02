package com.sellerprofit.admin.dto;

import com.sellerprofit.domain.User;
import com.sellerprofit.subscription.PlanType;

import java.time.OffsetDateTime;

/**
 * 관리자 화면용 유저 표현. 민감필드(비밀번호 해시·빌링키·customerKey 등)는 절대 포함하지 않는다
 * (docs/admin-tasks.md 절대 규칙 6).
 */
public record AdminUserView(
        Long userId,
        String email,
        OffsetDateTime createdAt,
        String role,
        String plan,                    // FREE | PRO (PlanType.fromStatus 기준)
        String subscriptionStatus,      // FREE | ACTIVE | PAST_DUE | CANCELED
        OffsetDateTime currentPeriodEnd,
        String source                   // PAID | COMP
) {
    public static AdminUserView of(User user) {
        PlanType plan = PlanType.fromStatus(user.getSubscriptionStatus());
        return new AdminUserView(
                user.getId(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getRole().name(),
                plan.name(),
                user.getSubscriptionStatus().name(),
                user.getCurrentPeriodEnd(),
                user.getSource().name());
    }
}
