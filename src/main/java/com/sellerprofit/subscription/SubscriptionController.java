package com.sellerprofit.subscription;

import com.sellerprofit.auth.CurrentUser;
import com.sellerprofit.subscription.dto.PlanView;
import com.sellerprofit.subscription.dto.SubscriptionView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 요금제/구독 조회 API.
 *
 * 예) GET /api/plans          → 요금제 목록(무료 포함, 요금 페이지용, 공개)
 *     GET /api/subscription   → 로그인 유저의 현재 구독 상태(세션 주체)
 *
 * ※ /api/subscription 은 로그인 필수. userId 를 직접 받지 않고 세션 주체로 정한다(타인 구독 조회 차단).
 */
@RestController
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final CurrentUser currentUser;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  CurrentUser currentUser) {
        this.subscriptionService = subscriptionService;
        this.currentUser = currentUser;
    }

    @GetMapping("/api/plans")
    public List<PlanView> plans() {
        return subscriptionService.catalog();
    }

    @GetMapping("/api/subscription")
    public SubscriptionView subscription(HttpServletRequest http) {
        return subscriptionService.forUser(currentUser.requireUserId(http));
    }
}
