package com.sellerprofit.admin;

import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T10.4 — AdminRoleService: role 변경 + 마지막 ADMIN 잠금 방지(docs/admin-tasks.md 절대 규칙 3).
 */
class AdminRoleServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminAuditService adminAuditService = mock(AdminAuditService.class);
    private final AdminRoleService adminRoleService = new AdminRoleService(userRepository, adminAuditService);

    @Test
    void USER를_ADMIN으로_승격_정상() {
        User target = user(2L, Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        adminRoleService.changeRole(1L, 2L, "ADMIN");

        assertEquals(Role.ADMIN, target.getRole());
        verify(adminAuditService).record(eq(1L), eq("CHANGE_ROLE"), eq(2L), anyMap());
    }

    @Test
    void 다른_관리자가_있으면_ADMIN을_USER로_강등_가능() {
        User target = user(2L, Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(2L); // 자신 포함 2명

        adminRoleService.changeRole(1L, 2L, "USER");

        assertEquals(Role.USER, target.getRole());
        verify(adminAuditService).record(eq(1L), eq("CHANGE_ROLE"), eq(2L), anyMap());
    }

    @Test
    void 마지막_ADMIN을_USER로_바꾸면_400() {
        User target = user(1L, Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(target));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        // 다른 관리자(actorId=9L)가 마지막 남은 ADMIN(target=1L)을 강등 시도해도 막혀야 한다
        assertThrows(IllegalArgumentException.class,
                () -> adminRoleService.changeRole(9L, 1L, "USER"));
        assertEquals(Role.ADMIN, target.getRole());
        verify(adminAuditService, never()).record(anyLong(), anyString(), anyLong(), anyMap());
    }

    @Test
    void 자기_자신의_ADMIN_해제는_관리자가_더_있어도_불가() {
        User self = user(1L, Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(5L); // 다른 관리자 충분히 있어도

        assertThrows(IllegalArgumentException.class,
                () -> adminRoleService.changeRole(1L, 1L, "USER"));
        assertEquals(Role.ADMIN, self.getRole());
    }

    @Test
    void 동일한_role이면_변경없음_감사로그도_없음() {
        User target = user(2L, Role.USER);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        adminRoleService.changeRole(1L, 2L, "USER");

        assertEquals(Role.USER, target.getRole());
        verify(adminAuditService, never()).record(anyLong(), anyString(), anyLong(), anyMap());
    }

    @Test
    void 잘못된_role값은_400() {
        assertThrows(IllegalArgumentException.class,
                () -> adminRoleService.changeRole(1L, 2L, "SUPERADMIN"));
        assertThrows(IllegalArgumentException.class,
                () -> adminRoleService.changeRole(1L, 2L, ""));
        assertThrows(IllegalArgumentException.class,
                () -> adminRoleService.changeRole(1L, 2L, null));
    }

    private static User user(Long id, Role role) {
        User u = User.create("u" + id + "@test.local", "hash");
        u.setId(id);
        u.setRole(role);
        return u;
    }
}
