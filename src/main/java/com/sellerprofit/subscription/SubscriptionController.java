package com.sellerprofit.subscription;

import com.sellerprofit.subscription.dto.PlanView;
import com.sellerprofit.subscription.dto.SubscriptionView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 요금제/구독 조회 API.
 *
 * 예) GET /api/plans              → 요금제 목록(무료 포함, 요금 페이지용)
 *     GET /api/subscription?userId=1 → 해당 유저의 현재 구독 상태
 *
 * ※ 로그인(세션) 연동 전이라 userId 를 직접 받는다. Phase 2 에서 인증 주체로 대체한다.
 */
@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/api/plans")
    public List<PlanView> plans() {
        return subscriptionService.catalog();
    }

    @GetMapping("/api/subscription")
    public SubscriptionView subscription(@RequestParam Long userId) {
        return subscriptionService.forUser(userId);
    }
}
