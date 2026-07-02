package com.sellerprofit.subscription;

import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.SubscriptionSource;
import com.sellerprofit.domain.type.SubscriptionStatus;
import com.sellerprofit.repository.UserRepository;
import com.sellerprofit.subscription.dto.CompGrantResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T10.3 — SubscriptionService.grantComp (관리자 무상 PRO 지급).
 *
 * docs/admin-tasks.md: source=COMP 로 저장(결제와 분리), 만료일은
 * max(now, 기존 만료일)+N개월(연장), 잘못된 개월(0 이하) 은 400(IllegalArgumentException).
 */
class SubscriptionServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final SubscriptionService subscriptionService = new SubscriptionService(userRepository);

    @Test
    void 기존_만료일이_없으면_지금부터_N개월_지급() {
        User user = User.create("nofree@test.local", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        OffsetDateTime before = OffsetDateTime.now(KST);
        CompGrantResult result = subscriptionService.grantComp(1L, 3);
        OffsetDateTime after = OffsetDateTime.now(KST);

        assertNull(result.before());
        assertEquals(SubscriptionStatus.ACTIVE, user.getSubscriptionStatus());
        assertEquals(SubscriptionSource.COMP, user.getSource());
        // 새 만료일은 (지급 호출 전후 now) + 3개월 사이여야 한다
        assertEquals(true, !user.getCurrentPeriodEnd().isBefore(before.plusMonths(3)));
        assertEquals(true, !user.getCurrentPeriodEnd().isAfter(after.plusMonths(3)));
    }

    @Test
    void 기존_만료일이_미래면_그_위에_N개월_연장() {
        User user = User.create("active@test.local", "hash");
        OffsetDateTime existingEnd = OffsetDateTime.now(KST).plusMonths(2);
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setCurrentPeriodEnd(existingEnd);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        CompGrantResult result = subscriptionService.grantComp(2L, 1);

        assertEquals(existingEnd, result.before());
        assertEquals(existingEnd.plusMonths(1), result.after());
        assertEquals(existingEnd.plusMonths(1), user.getCurrentPeriodEnd());
        assertEquals(SubscriptionSource.COMP, user.getSource());
    }

    @Test
    void 기존_만료일이_지났으면_지금부터_N개월() {
        User user = User.create("expired@test.local", "hash");
        OffsetDateTime pastEnd = OffsetDateTime.now(KST).minusDays(10);
        user.setSubscriptionStatus(SubscriptionStatus.FREE);
        user.setCurrentPeriodEnd(pastEnd);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        OffsetDateTime before = OffsetDateTime.now(KST);
        CompGrantResult result = subscriptionService.grantComp(3L, 2);
        OffsetDateTime after = OffsetDateTime.now(KST);

        assertEquals(pastEnd, result.before());
        assertEquals(true, !result.after().isBefore(before.plusMonths(2)));
        assertEquals(true, !result.after().isAfter(after.plusMonths(2)));
    }

    @Test
    void months가_0이하면_400_IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> subscriptionService.grantComp(1L, 0));
        assertThrows(IllegalArgumentException.class, () -> subscriptionService.grantComp(1L, -1));
    }

    @Test
    void 존재하지_않는_유저면_IllegalArgumentException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> subscriptionService.grantComp(99L, 1));
    }
}
