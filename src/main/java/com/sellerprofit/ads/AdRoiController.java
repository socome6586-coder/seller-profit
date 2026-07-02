package com.sellerprofit.ads;

import com.sellerprofit.account.AccountAccess;
import com.sellerprofit.ads.dto.AdRoiSummary;
import com.sellerprofit.auth.CurrentUser;
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
 * 광고 ROI 대시보드 API.
 *
 * 예) GET /api/dashboard/ad-roi?accountId=1&from=2026-06-01&to=2026-06-29
 *     from/to 생략 시 최근 30일(KST 기준) — 기존 {@code /api/dashboard/profit} 과 동일 관례.
 *
 * ※ 로그인 필수. accountId 는 세션 유저 소유인지 확인한 뒤에만 조회한다(타 셀러 데이터 차단).
 */
@RestController
@RequestMapping("/api/dashboard")
public class AdRoiController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdRoiService adRoiService;
    private final CurrentUser currentUser;
    private final AccountAccess accountAccess;
    private final SubscriptionService subscriptionService;

    public AdRoiController(AdRoiService adRoiService,
                           CurrentUser currentUser,
                           AccountAccess accountAccess,
                           SubscriptionService subscriptionService) {
        this.adRoiService = adRoiService;
        this.currentUser = currentUser;
        this.accountAccess = accountAccess;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/ad-roi")
    public AdRoiSummary adRoi(
            @RequestParam Long accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest http) {
        Long userId = currentUser.requireUserId(http);
        accountAccess.assertOwner(accountId, userId);
        LocalDate end = (to != null) ? to : LocalDate.now(KST);
        LocalDate start = (from != null) ? from : end.minusDays(29);
        subscriptionService.assertWithinLookback(userId, start, end);
        return adRoiService.calculate(accountId, start, end);
    }
}
