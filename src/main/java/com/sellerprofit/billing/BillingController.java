package com.sellerprofit.billing;

import com.sellerprofit.auth.AuthController;
import com.sellerprofit.auth.UnauthorizedException;
import com.sellerprofit.billing.dto.SubscribeRequest;
import com.sellerprofit.subscription.dto.SubscriptionView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 구독 결제 API (토스 빌링). 로그인(세션) 필수 — 결제 주체는 세션의 userId 로 정한다.
 *
 * 예) GET  /api/billing/status     → 결제 기능 사용 가능 여부(키 설정됨?)
 *     POST /api/billing/subscribe  {"authKey":"..."} → 구독 시작(첫 결제 + ACTIVE)
 *     POST /api/billing/cancel     → 해지(남은 기간 유지)
 *
 * ※ Phase 3 스캐폴딩: 토스 키 미설정 시 subscribe 는 503(IllegalStateException)으로 막힌다.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    /** 결제 기능 활성화 여부(키 주입 여부). 로그인 불필요 — 버튼 노출 판단용. */
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("enabled", billingService.billingEnabled());
    }

    @PostMapping("/subscribe")
    public SubscriptionView subscribe(@Valid @RequestBody SubscribeRequest request,
                                      HttpServletRequest http) {
        return billingService.subscribe(requireUserId(http), request.authKey());
    }

    @PostMapping("/cancel")
    public SubscriptionView cancel(HttpServletRequest http) {
        return billingService.cancel(requireUserId(http));
    }

    /** 세션에서 로그인 유저 id 를 꺼낸다. 없으면 401. */
    private Long requireUserId(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        Object userId = session == null ? null : session.getAttribute(AuthController.SESSION_USER_ID);
        if (userId == null) {
            throw new UnauthorizedException("로그인이 필요합니다.");
        }
        return (Long) userId;
    }
}
