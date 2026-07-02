package com.sellerprofit.billing;

import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.SubscriptionSource;
import com.sellerprofit.domain.type.SubscriptionStatus;
import com.sellerprofit.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T10.6 — 빌링 스케줄러의 COMP(무상 지급) 구독 분기(docs/admin-tasks.md "빌링 스케줄러 상호작용").
 *
 * COMP 구독은 빌링키가 없으므로 결제를 절대 시도하면 안 되지만, 만료(주기 경과)는 PAID 와 동일하게
 * FREE 로 강등돼야 한다. PAID 구독의 기존 동작(결제 성공→연장, 실패→PAST_DUE, 빌링키 없음→PAST_DUE)은
 * 이 변경으로 회귀가 없어야 한다.
 */
class BillingServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TossBillingClient billingClient = mock(TossBillingClient.class);
    private final BillingService billingService = new BillingService(userRepository, billingClient);

    @Test
    void COMP_구독은_결제를_시도하지_않고_만료시_FREE_로_강등된다() {
        OffsetDateTime now = OffsetDateTime.now(KST);
        User comp = activeUser("comp@test.local", SubscriptionSource.COMP, null, now.minusDays(1));
        when(userRepository.findBySubscriptionStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, now))
                .thenReturn(List.of(comp));

        int charged = billingService.renewDue(now);

        assertEquals(0, charged);
        assertEquals(SubscriptionStatus.FREE, comp.getSubscriptionStatus());
        verify(billingClient, never()).charge(anyString(), anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    void PAID_구독_결제_성공시_ACTIVE_로_연장된다_회귀없음() {
        OffsetDateTime now = OffsetDateTime.now(KST);
        User paid = activeUser("paid@test.local", SubscriptionSource.PAID, "billing-key-1", now.minusDays(1));
        when(userRepository.findBySubscriptionStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, now))
                .thenReturn(List.of(paid));
        when(billingClient.charge(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(Map.of("status", "DONE"));

        int charged = billingService.renewDue(now);

        assertEquals(1, charged);
        assertEquals(SubscriptionStatus.ACTIVE, paid.getSubscriptionStatus());
        verify(billingClient, times(1)).charge(anyString(), anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    void PAID_구독_빌링키_없으면_결제없이_PAST_DUE_회귀없음() {
        OffsetDateTime now = OffsetDateTime.now(KST);
        User paid = activeUser("paid-nokey@test.local", SubscriptionSource.PAID, null, now.minusDays(1));
        when(userRepository.findBySubscriptionStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, now))
                .thenReturn(List.of(paid));

        int charged = billingService.renewDue(now);

        assertEquals(0, charged);
        assertEquals(SubscriptionStatus.PAST_DUE, paid.getSubscriptionStatus());
        verify(billingClient, never()).charge(anyString(), anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    void PAID_구독_결제_실패시_PAST_DUE_회귀없음() {
        OffsetDateTime now = OffsetDateTime.now(KST);
        User paid = activeUser("paid-fail@test.local", SubscriptionSource.PAID, "billing-key-2", now.minusDays(1));
        when(userRepository.findBySubscriptionStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, now))
                .thenReturn(List.of(paid));
        when(billingClient.charge(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenThrow(new RuntimeException("카드 한도초과"));

        int charged = billingService.renewDue(now);

        assertEquals(0, charged);
        assertEquals(SubscriptionStatus.PAST_DUE, paid.getSubscriptionStatus());
    }

    @Test
    void 여러_유저_섞여있어도_COMP_는_결제호출_0회_PAID_만_청구된다() {
        OffsetDateTime now = OffsetDateTime.now(KST);
        User comp = activeUser("comp2@test.local", SubscriptionSource.COMP, null, now.minusDays(1));
        User paid = activeUser("paid2@test.local", SubscriptionSource.PAID, "billing-key-3", now.minusDays(1));
        when(userRepository.findBySubscriptionStatusAndCurrentPeriodEndBefore(SubscriptionStatus.ACTIVE, now))
                .thenReturn(List.of(comp, paid));
        when(billingClient.charge(anyString(), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(Map.of("status", "DONE"));

        int charged = billingService.renewDue(now);

        assertEquals(1, charged);
        assertEquals(SubscriptionStatus.FREE, comp.getSubscriptionStatus());
        assertEquals(SubscriptionStatus.ACTIVE, paid.getSubscriptionStatus());
        verify(billingClient, times(1)).charge(anyString(), anyString(), anyInt(), anyString(), anyString());
    }

    private static User activeUser(String email, SubscriptionSource source, String billingKey,
                                    OffsetDateTime currentPeriodEnd) {
        User user = User.create(email, "hash");
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSource(source);
        user.setBillingKey(billingKey);
        user.setBillingCustomerKey("cust_" + email);
        user.setCurrentPeriodEnd(currentPeriodEnd);
        return user;
    }
}
