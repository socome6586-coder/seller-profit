package com.sellerprofit.admin;

import com.sellerprofit.auth.CurrentUser;
import com.sellerprofit.auth.UnauthorizedException;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T10.1 — /api/admin/** 서버측 권한 가드.
 *
 * docs/admin-tasks.md 절대 규칙 1: 권한은 서버에서 강제, 비관리자는 403(존재/이유 노출 없이).
 * CurrentUser 가 세션이 없을 때 이미 401 을 던지므로, 여기서는 "로그인은 했지만 ADMIN 이 아닌" 경우와
 * "ADMIN 인" 경우, 그리고 세션이 아예 없는 경우(401 로 위임)를 각각 증명한다.
 */
class AdminAccessTest {

    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminAccess adminAccess = new AdminAccess(currentUser, userRepository);
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    void ADMIN_유저는_통과한다() {
        User admin = user(1L, Role.ADMIN);
        when(currentUser.requireUserId(request)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        User result = adminAccess.requireAdmin(request);

        assertEquals(admin, result);
    }

    @Test
    void 일반_USER_는_403_ForbiddenException() {
        User normal = user(2L, Role.USER);
        when(currentUser.requireUserId(request)).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(normal));

        assertThrows(ForbiddenException.class, () -> adminAccess.requireAdmin(request));
    }

    @Test
    void 세션은_있지만_유저가_사라졌으면_403() {
        when(currentUser.requireUserId(request)).thenReturn(999L);
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class, () -> adminAccess.requireAdmin(request));
    }

    @Test
    void 세션이_없으면_401_은_CurrentUser_가_그대로_던진다() {
        when(currentUser.requireUserId(request)).thenThrow(new UnauthorizedException("로그인이 필요합니다."));

        assertThrows(UnauthorizedException.class, () -> adminAccess.requireAdmin(request));
    }

    private static User user(Long id, Role role) {
        User u = User.create("u" + id + "@test.local", "hash");
        u.setRole(role);
        return u;
    }
}
