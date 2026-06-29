package com.sellerprofit.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

/**
 * 세션에서 현재 로그인 유저 id 를 꺼내는 공통 헬퍼.
 *
 * <p>컨트롤러마다 세션 키를 직접 읽던 로직을 한 곳으로 모은다. 세션이 없으면
 * {@link UnauthorizedException}(→ 401). 보호가 필요한 엔드포인트는 이걸로 주체를 정한다.
 */
@Component
public class CurrentUser {

    /** 로그인 필수. 세션의 userId 를 돌려주고, 없으면 401. */
    public Long requireUserId(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        Object userId = session == null ? null : session.getAttribute(AuthController.SESSION_USER_ID);
        if (userId == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        return (Long) userId;
    }
}
