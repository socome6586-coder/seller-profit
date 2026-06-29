package com.sellerprofit.auth;

import com.sellerprofit.auth.dto.AuthUserView;
import com.sellerprofit.auth.dto.LoginRequest;
import com.sellerprofit.auth.dto.SignupRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API. 세션(쿠키) 기반이며, 별도 시큐리티 필터체인 없이 컨트롤러가 직접 세션을 다룬다.
 *
 * 예) POST /api/auth/signup  {"email":"a@b.com","password":"secret123"} → 무료 가입
 *     POST /api/auth/login   {"email":"a@b.com","password":"secret123"} → 세션에 userId 저장
 *     POST /api/auth/logout  → 세션 무효화(204)
 *     GET  /api/auth/me      → 현재 로그인 유저(세션 없으면 401)
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 로그인 세션에 userId 를 담는 키. me/보호 로직에서 동일 키로 읽는다. */
    public static final String SESSION_USER_ID = "USER_ID";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserView signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request.email(), request.password());
    }

    /** 로그인 성공 시 세션에 userId 를 저장하고 유저 표현을 돌려준다. */
    @PostMapping("/login")
    public AuthUserView login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthUserView user = authService.login(request.email(), request.password());
        // 세션 고정 공격 방지: 로그인 시점에 새 세션을 발급한다.
        HttpSession old = http.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        http.getSession(true).setAttribute(SESSION_USER_ID, user.userId());
        return user;
    }

    /** 로그아웃: 세션이 있으면 무효화. 멱등(없어도 204). */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    /** 현재 로그인 유저. 세션이 없으면 401(UnauthorizedException). */
    @GetMapping("/me")
    public AuthUserView me(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        return authService.me((Long) userId);
    }
}
