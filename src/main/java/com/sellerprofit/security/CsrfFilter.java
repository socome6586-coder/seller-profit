package com.sellerprofit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * CSRF 방어 — 더블 서브밋 쿠키(double-submit cookie) 패턴.
 *
 * <p>보안 감사(2026-07) 후속 조치. 이 앱은 Spring Security 없이 {@code HttpSession} 을 직접 다루는
 * 수제 인증(AuthController 참고)이라 프레임워크 차원의 CSRF 방어가 전혀 없었다. 세션 쿠키만으로
 * 인증하는 구조에서는 공격자 사이트가 피해자 브라우저를 통해 상태 변경 요청(로그인 쿠키가 자동
 * 첨부됨)을 위조해 보낼 수 있어, 별도 토큰 검증이 필요하다.
 *
 * <p>동작:
 * <ol>
 *   <li>모든 요청에서 {@code XSRF-TOKEN} 쿠키가 없으면 새로 발급한다(자바스크립트가 읽어야 하므로
 *       {@code HttpOnly} 를 끈다 — 세션 쿠키와 달리 이 토큰 자체는 비밀값이 아니라 "쿠키를 읽고
 *       그대로 헤더에 되돌려줄 수 있는지"를 확인하는 용도라 안전하다).</li>
 *   <li>상태 변경 메서드(POST/PUT/PATCH/DELETE)의 {@code /api/**} 요청은 {@code X-XSRF-TOKEN} 헤더
 *       값이 {@code XSRF-TOKEN} 쿠키 값과 정확히 일치해야 통과한다. 교차 사이트 공격자는 피해자의
 *       쿠키 값을 읽을 수 없으므로(동일-출처 정책) 이 헤더를 위조할 수 없다.</li>
 * </ol>
 *
 * <p>서버-서버 콜백(웹훅) 엔드포인트는 현재 존재하지 않아(전부 브라우저발 상태 변경 요청) 별도
 * 예외 경로를 두지 않았다. 나중에 웹훅을 추가하면 그 경로는 이 필터에서 제외해야 한다.
 */
@Component
@Order(1)
public class CsrfFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final int TOKEN_BYTES = 32;
    /** 쿠키 자체 수명 — 세션과 별개로 넉넉히(30일) 둔다. 토큰이 새어나가도 위험도가 낮기 때문. */
    private static final long COOKIE_MAX_AGE_SECONDS = 60L * 60 * 24 * 30;

    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String existing = readCookie(request);
        String token = existing;
        if (token == null) {
            token = generateToken();
            setCookie(response, token, request.isSecure());
        }

        if (!SAFE_METHODS.contains(request.getMethod()) && request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
            String header = request.getHeader(HEADER_NAME);
            if (existing == null || header == null || !constantTimeEquals(existing, header)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"CSRF 토큰이 유효하지 않습니다. 새로고침 후 다시 시도해 주세요.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName()) && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private static void setCookie(HttpServletResponse response, String token, boolean secure) {
        // Servlet Cookie API 는 SameSite 를 지원하지 않아 ResponseCookie(Spring) 로 Set-Cookie 헤더를 직접 만든다.
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(false)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE_SECONDS)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
