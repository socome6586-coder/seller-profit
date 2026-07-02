package com.sellerprofit.profit;

import com.sellerprofit.account.AccountAccess;
import com.sellerprofit.auth.CurrentUser;
import com.sellerprofit.profit.dto.ProfitSummary;
import com.sellerprofit.profit.dto.ReturnReasonSummary;
import com.sellerprofit.subscription.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 대시보드 조회 API. 기간 선택 → 상품별 순이익/마진율 표(적자 상품이 위로).
 *
 * 예) GET /api/dashboard/profit?accountId=1&from=2026-06-01&to=2026-06-29
 *     GET /api/dashboard/returns?accountId=1  → 반품 사유별 분포
 *     from/to 생략 시 최근 30일(KST 기준).
 *
 * ※ 로그인 필수. accountId 는 세션 유저 소유인지 확인한 뒤에만 조회한다(타 셀러 데이터 차단).
 */
@RestController
@RequestMapping("/api/dashboard")
public class ProfitDashboardController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProfitCalculationService profitCalculationService;
    private final ReturnStatsService returnStatsService;
    private final CurrentUser currentUser;
    private final AccountAccess accountAccess;
    private final SubscriptionService subscriptionService;

    public ProfitDashboardController(ProfitCalculationService profitCalculationService,
                                     ReturnStatsService returnStatsService,
                                     CurrentUser currentUser,
                                     AccountAccess accountAccess,
                                     SubscriptionService subscriptionService) {
        this.profitCalculationService = profitCalculationService;
        this.returnStatsService = returnStatsService;
        this.currentUser = currentUser;
        this.accountAccess = accountAccess;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/profit")
    public ProfitSummary profit(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest http) {
        Long userId = currentUser.requireUserId(http);
        accountAccess.assertOwner(accountId, userId);
        LocalDate end = (to != null) ? to : LocalDate.now(KST);
        LocalDate start = (from != null) ? from : end.minusDays(29);
        subscriptionService.assertWithinLookback(userId, start, end);
        return profitCalculationService.calculate(accountId, start, end);
    }

    @GetMapping("/returns")
    public ReturnReasonSummary returns(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest http) {
        Long userId = currentUser.requireUserId(http);
        accountAccess.assertOwner(accountId, userId);
        LocalDate end = (to != null) ? to : LocalDate.now(KST);
        LocalDate start = (from != null) ? from : end.minusDays(29);
        subscriptionService.assertWithinLookback(userId, start, end);
        return returnStatsService.byReason(accountId, start, end);
    }
}
