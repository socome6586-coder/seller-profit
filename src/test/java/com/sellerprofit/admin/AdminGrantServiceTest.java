package com.sellerprofit.admin;

import com.sellerprofit.subscription.SubscriptionService;
import com.sellerprofit.subscription.dto.CompGrantResult;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T10.3 — AdminGrantService: 입력검증(400) + SubscriptionService 경유(도메인 우회 금지) +
 * 감사 로그 1건 기록을 증명한다.
 */
class AdminGrantServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final AdminAuditService adminAuditService = mock(AdminAuditService.class);
    private final AdminGrantService adminGrantService =
            new AdminGrantService(subscriptionService, adminAuditService);

    @Test
    void 정상_지급시_SubscriptionService_경유_후_감사로그_1건() {
        OffsetDateTime before = OffsetDateTime.now(KST);
        OffsetDateTime after = before.plusMonths(3);
        when(subscriptionService.grantComp(10L, 3)).thenReturn(new CompGrantResult(before, after));

        adminGrantService.grantPro(1L, 10L, 3, "PRO");

        verify(subscriptionService).grantComp(10L, 3);
        verify(adminAuditService).record(eq(1L), eq("GRANT_PLAN"), eq(10L), anyMap());
    }

    @Test
    void months가_null이면_400() {
        assertThrows(IllegalArgumentException.class,
                () -> adminGrantService.grantPro(1L, 10L, null, "PRO"));
        verify(subscriptionService, never()).grantComp(anyLong(), anyInt());
        verify(adminAuditService, never()).record(anyLong(), anyString(), anyLong(), anyMap());
    }

    @Test
    void months가_0이하면_400() {
        assertThrows(IllegalArgumentException.class,
                () -> adminGrantService.grantPro(1L, 10L, 0, "PRO"));
        verify(subscriptionService, never()).grantComp(anyLong(), anyInt());
    }

    @Test
    void plan이_PRO가_아니면_400() {
        assertThrows(IllegalArgumentException.class,
                () -> adminGrantService.grantPro(1L, 10L, 3, "FREE"));
        verify(subscriptionService, never()).grantComp(anyLong(), anyInt());
    }
}
