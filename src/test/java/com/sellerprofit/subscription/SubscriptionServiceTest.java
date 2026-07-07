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

    @Test
    void COMP_구독은_회수시_FREE_로_강등하고_만료일도_비운다() {
        User user = User.create("comp@test.local", "hash");
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSource(SubscriptionSource.COMP);
        user.setCurrentPeriodEnd(OffsetDateTime.now(KST).plusMonths(10));
        when(userRepository.findById(4L)).thenReturn(Optional.of(user));

        SubscriptionStatus before = subscriptionService.revokeComp(4L);

        assertEquals(SubscriptionStatus.ACTIVE, before);
        assertEquals(SubscriptionStatus.FREE, user.getSubscriptionStatus());
        // 회수 전 관리자 화면에서 실제로 보고된 버그: FREE 로 바뀌어도 예전 만료일이 남아있었다.
        assertNull(user.getCurrentPeriodEnd());
    }

    @Test
    void PAID_구독은_회수_대상이_아니라_400() {
        User user = User.create("paid@test.local", "hash");
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSource(SubscriptionSource.PAID);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> subscriptionService.revokeComp(5L));
        assertEquals(SubscriptionStatus.ACTIVE, user.getSubscriptionStatus());
    }

    @Test
    void 이미_FREE인_COMP_구독은_다시_회수할_수_없다_400() {
        // 관리자 화면 버그: 이미 회수된(= 회수할 지급이 없는) COMP 유저의 회수 버튼이 계속
        // 활성 상태로 남아 다시 눌리던 문제의 서버측 방어. before=FREE/after=FREE 인 의미없는
        // 감사 로그가 쌓이지 않도록 여기서 400 으로 막는다.
        User user = User.create("alreadyrevoked@test.local", "hash");
        user.setSubscriptionStatus(SubscriptionStatus.FREE);
        user.setSource(SubscriptionSource.COMP);
        when(userRepository.findById(6L)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> subscriptionService.revokeComp(6L));
        assertEquals(SubscriptionStatus.FREE, user.getSubscriptionStatus());
    }
}
