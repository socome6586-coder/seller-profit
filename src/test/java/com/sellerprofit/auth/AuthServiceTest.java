package com.sellerprofit.auth;

import com.sellerprofit.admin.AdminBootstrapService;
import com.sellerprofit.auth.dto.AuthUserView;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.SubscriptionSource;
import com.sellerprofit.domain.type.SubscriptionStatus;
import com.sellerprofit.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminBootstrapService adminBootstrapService = mock(AdminBootstrapService.class);
    private final AuthService authService = new AuthService(userRepository, passwordEncoder, adminBootstrapService);

    @Test
    void 가입하면_한달_PRO_COMP_무료지급으로_시작한다() {
        when(userRepository.existsByEmail("new@test.local")).thenReturn(false);
        when(userRepository.existsByPhone("01012345678")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OffsetDateTime before = OffsetDateTime.now(KST);
        AuthUserView view = authService.signup("NEW@Test.Local", "secret123", "010-1234-5678");
        OffsetDateTime after = OffsetDateTime.now(KST);

        assertEquals("new@test.local", view.email());
        assertEquals(SubscriptionStatus.ACTIVE.name(), view.subscriptionStatus());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals(SubscriptionStatus.ACTIVE, saved.getSubscriptionStatus());
        assertEquals(SubscriptionSource.COMP, saved.getSource());
        assertFalse(saved.getCurrentPeriodEnd().isBefore(before.plusMonths(1)));
        assertTrue(saved.getCurrentPeriodEnd().isBefore(after.plusMonths(1).plusSeconds(1)));
        verify(adminBootstrapService).promoteIfBootstrapAdmin(saved);
    }
}
