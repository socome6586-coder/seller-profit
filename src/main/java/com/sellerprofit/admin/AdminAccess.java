package com.sellerprofit.admin;

import com.sellerprofit.auth.CurrentUser;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * {@code /api/admin/**} 전용 서버측 권한 가드.
 *
 * <p>UI 에서 관리자 링크를 숨기는 것은 편의일 뿐이고, 실제 방어는 반드시 여기서 이뤄진다
 * (docs/admin-tasks.md 절대 규칙 1). 모든 관리자 컨트롤러 메서드는 진입 즉시 이 가드를 호출해야 한다.
 * {@link CurrentUser} 와 동일하게, 컨트롤러마다 세션/권한 검사를 직접 흩어 적지 않고 한 곳에 모은다.
 */
@Component
public class AdminAccess {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;

    public AdminAccess(CurrentUser currentUser, UserRepository userRepository) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;
    }

    /**
     * 로그인 + ADMIN role 필수.
     * 세션이 없으면 401({@link com.sellerprofit.auth.UnauthorizedException}),
     * 로그인은 했지만 ADMIN 이 아니면 403({@link ForbiddenException}).
     *
     * @return 검증을 통과한 관리자 User(감사 로그의 admin_user_id 등에 재사용)
     */
    public User requireAdmin(HttpServletRequest http) {
        Long userId = currentUser.requireUserId(http);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("접근 권한이 없습니다."));
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }
        return user;
    }
}
