package com.sellerprofit.admin;

import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.repository.UserRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * T10.1 — app.admin-emails 부트스트랩 승격 로직.
 *
 * docs/admin-tasks.md 절대 규칙 2: 관리자가 없으면 아무도 승격 못 하므로, 설정(시크릿)에 있는
 * 이메일은 로그인/기동 시 ADMIN 으로 승격. 대소문자/공백 무관, 목록에 없으면 그대로 USER.
 */
class AdminBootstrapServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    @Test
    void 목록에_있는_이메일은_ADMIN_으로_승격된다() {
        AdminBootstrapService service = new AdminBootstrapService(
                "owner@seller-profit.local, second@seller-profit.local", userRepository);
        User user = User.create("owner@seller-profit.local", "hash");

        service.promoteIfBootstrapAdmin(user);

        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    void 대소문자_공백은_무시하고_매칭한다() {
        AdminBootstrapService service = new AdminBootstrapService(
                "  Owner@Seller-Profit.local  ", userRepository);
        User user = User.create("owner@seller-profit.local", "hash");

        service.promoteIfBootstrapAdmin(user);

        assertEquals(Role.ADMIN, user.getRole());
    }

    @Test
    void 목록에_없으면_USER_로_유지된다() {
        AdminBootstrapService service = new AdminBootstrapService(
                "owner@seller-profit.local", userRepository);
        User user = User.create("nobody@seller-profit.local", "hash");

        service.promoteIfBootstrapAdmin(user);

        assertEquals(Role.USER, user.getRole());
    }

    @Test
    void 빈_설정이면_아무도_승격되지_않는다() {
        AdminBootstrapService service = new AdminBootstrapService("", userRepository);
        User user = User.create("owner@seller-profit.local", "hash");

        service.promoteIfBootstrapAdmin(user);

        assertEquals(Role.USER, user.getRole());
    }

    @Test
    void 이미_ADMIN_이면_그대로_멱등() {
        AdminBootstrapService service = new AdminBootstrapService(
                "owner@seller-profit.local", userRepository);
        User user = User.create("owner@seller-profit.local", "hash");
        user.setRole(Role.ADMIN);

        service.promoteIfBootstrapAdmin(user);

        assertEquals(Role.ADMIN, user.getRole());
    }
}
