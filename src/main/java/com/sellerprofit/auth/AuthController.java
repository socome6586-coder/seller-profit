package com.sellerprofit.auth;

import com.sellerprofit.auth.dto.AuthUserView;
import com.sellerprofit.auth.dto.LoginRequest;
import com.sellerprofit.auth.dto.PasswordResetConfirmRequest;
import com.sellerprofit.auth.dto.PasswordResetRequestRequest;
import com.sellerprofit.auth.dto.SignupRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 인증 API. 세션(쿠키) 기반이며, 별도 시큐리티 필터체인 없이 컨트롤러가 직접 세션을 다룬다.
 *
 * 예) GET  /api/auth/check-email?email=a@b.com → {"available":true|false}
 *     POST /api/auth/signup  {"email":"a@b.com","password":"secret123","phone":"01012345678"} → 무료 가입
 *     POST /api/auth/login   {"email":"a@b.com","password":"secret123","remember":true} → 세션에 userId 저장
 *     POST /api/auth/logout  → 세션 무효화(204)
 *     GET  /api/auth/me      → 현재 로그인 유저(세션 없으면 401)
 *     POST /api/auth/password-reset/request {"email":"a@b.com"} → 항상 204(계정 존재 여부 노출 안 함)
 *     POST /api/auth/password-reset/confirm {"token":"...","newPassword":"..."} → 204
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 로그인 세션에 userId 를 담는 키. me/보호 로직에서 동일 키로 읽는다. */
    public static final String SESSION_USER_ID = "USER_ID";

    /** 자동로그인("로그인 상태 유지") 시 세션/쿠키 수명 — 30일. */
    private static final int REMEMBER_ME_SECONDS = 60 * 60 * 24 * 30;

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    /** 가입 폼의 이메일 "중복확인" 버튼이 호출한다. 존재 여부만 boolean 으로 응답. */
    @GetMapping("/check-email")
    public Map<String, Boolean> checkEmail(@RequestParam String email) {
        return Map.of("available", authService.isEmailAvailable(email));
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserView signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request.email(), request.password(), request.phone());
    }

    /**
     * 로그인 성공 시 세션에 userId 를 저장하고 유저 표현을 돌려준다.
     *
     * <p>{@code remember=true}(자동로그인/"로그인 상태 유지")면 세션 타임아웃과 세션 쿠키의
     * 수명을 30일로 늘린다. 기본(false)은 브라우저 세션 쿠키(브라우저 완전 종료 시 만료) +
     * 서버 기본 타임아웃 그대로다. 쿠키의 Secure 플래그는 {@code request.isSecure()} 로
     * 결정되므로, 운영(HTTPS, Caddy 뒤) 에서 정확히 동작하려면 {@code server.forward-headers-strategy}
     * 가 설정돼 있어야 한다(application.yml 참고).
     */
    @PostMapping("/login")
    public AuthUserView login(@Valid @RequestBody LoginRequest request, HttpServletRequest http,
                               HttpServletResponse response) {
        AuthUserView user = authService.login(request.email(), request.password());
        // 세션 고정 공격 방지: 로그인 시점에 새 세션을 발급한다.
        HttpSession old = http.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = http.getSession(true);
        session.setAttribute(SESSION_USER_ID, user.userId());

        if (request.remember()) {
            session.setMaxInactiveInterval(REMEMBER_ME_SECONDS);
            Cookie cookie = new Cookie("JSESSIONID", session.getId());
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setSecure(http.isSecure());
            cookie.setMaxAge(REMEMBER_ME_SECONDS);
            response.addCookie(cookie);
        }
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

    /**
     * 비밀번호 재설정 이메일 요청. 가입된 이메일이든 아니든 항상 204 — 응답만으로 계정 존재
     * 여부를 알 수 없게 한다(계정 열거 방지). SMTP 미설정 시 서버 로그에 링크가 남는다
     * (EmailService 참고).
     */
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest request) {
        passwordResetService.requestReset(request.email());
    }

    /** 재설정 토큰 확정: 새 비밀번호로 교체. 토큰이 유효하지 않거나 만료/사용됨이면 400. */
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
    }
}
