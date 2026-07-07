package com.sellerprofit.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * CSRF 더블 서브밋 쿠키 필터 단위 테스트. 보안 감사(2026-07) 후속 조치 — 실제 Spring MVC
 * 컨텍스트 없이 필터를 직접 호출해 핵심 규칙(쿠키 발급, 상태 변경 요청의 헤더/쿠키 일치 검증,
 * GET·비-API 경로는 통과)을 검증한다.
 */
class CsrfFilterTest {

    private final CsrfFilter filter = new CsrfFilter();

    @Test
    void GET_요청은_쿠키가_없으면_새로_발급하고_통과시킨다() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getHeader("Set-Cookie")).contains(CsrfFilter.COOKIE_NAME + "=");
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void POST_api_요청은_쿠키가_없으면_403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void POST_api_요청은_쿠키만_있고_헤더가_없으면_403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setCookies(new jakarta.servlet.http.Cookie(CsrfFilter.COOKIE_NAME, "abc123"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void POST_api_요청은_쿠키와_헤더가_일치하지_않으면_403() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setCookies(new jakarta.servlet.http.Cookie(CsrfFilter.COOKIE_NAME, "abc123"));
        req.addHeader(CsrfFilter.HEADER_NAME, "different-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(req, res);
    }

    @Test
    void POST_api_요청은_쿠키와_헤더가_일치하면_통과한다() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setCookies(new jakarta.servlet.http.Cookie(CsrfFilter.COOKIE_NAME, "abc123"));
        req.addHeader(CsrfFilter.HEADER_NAME, "abc123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(req, res);
    }

    @Test
    void api_경로가_아니면_토큰_검증_없이_통과한다() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/some-non-api-path");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        verify(chain, times(1)).doFilter(req, res);
    }
}
