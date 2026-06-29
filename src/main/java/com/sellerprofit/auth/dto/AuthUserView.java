package com.sellerprofit.auth.dto;

import com.sellerprofit.domain.User;

/**
 * 인증/가입 응답용 최소 유저 표현(비밀번호 해시는 절대 노출하지 않는다).
 */
public record AuthUserView(
        Long userId,
        String email,
        String subscriptionStatus
) {
    public static AuthUserView of(User user) {
        return new AuthUserView(user.getId(), user.getEmail(),
                user.getSubscriptionStatus().name());
    }
}
