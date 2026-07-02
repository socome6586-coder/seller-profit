package com.sellerprofit.subscription;

import com.sellerprofit.domain.User;
import com.sellerprofit.repository.UserRepository;
import com.sellerprofit.subscription.dto.PlanView;
import com.sellerprofit.subscription.dto.SubscriptionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * 요금제 카탈로그 + 유저 구독 상태 조회.
 *
 * 결제(토스 빌링) 연동은 Phase 3 에서 붙는다. 여기서는 카탈로그 노출과 현재 상태 조회만 담당한다.
 */
@Service
public class SubscriptionService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;

    public SubscriptionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 공개 요금제 목록(요금 페이지용). */
    public List<PlanView> catalog() {
        return Arrays.stream(PlanType.values())
                .map(PlanView::of)
                .toList();
    }

    /** 한 유저의 현재 구독 상태 + 적용 플랜. */
    @Transactional(readOnly = true)
    public SubscriptionView forUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));
        PlanType plan = PlanType.fromStatus(user.getSubscriptionStatus());
        return new SubscriptionView(
                user.getSubscriptionStatus().name(),
                user.getCurrentPeriodEnd(),
                PlanView.of(plan));
    }

    /**
     * 대시보드/광고ROI 조회 기간이 플랜의 조회기간 한도({@link PlanType#dashboardLookbackDays()})를
     * 넘는지 검사한다. "최근 N일까지 조회 가능"을 문자 그대로 지킨다: 시작일이
     * (오늘 − (N-1))보다 이르면 거부한다(좁지만 아주 오래된 범위도 걸러낸다).
     *
     * ⚠️ 이건 프론트 PeriodPicker 의 maxRangeDays 안내와 반드시 같은 기준이어야 한다.
     * 서버 강제가 진짜 게이트고, UI 는 안내일 뿐이다 — API 를 직접 호출해도 우회할 수 없어야 한다.
     * 무제한(PRO, dashboardLookbackDays == -1)이면 통과.
     */
    @Transactional(readOnly = true)
    public void assertWithinLookback(Long userId, LocalDate start, LocalDate end) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));
        PlanType plan = PlanType.fromStatus(user.getSubscriptionStatus());
        int lookbackDays = plan.dashboardLookbackDays();
        if (lookbackDays < 0) return; // 무제한(PRO)

        LocalDate earliestAllowed = LocalDate.now(KST).minusDays(lookbackDays - 1L);
        if (start.isBefore(earliestAllowed)) {
            throw new IllegalArgumentException(
                    plan.displayName() + " 플랜은 최근 " + lookbackDays
                            + "일까지 조회할 수 있습니다. PRO 로 업그레이드하면 전체 기간을 조회할 수 있습니다.");
        }
    }
}
