package com.sellerprofit.subscription;

import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.SubscriptionSource;
import com.sellerprofit.domain.type.SubscriptionStatus;
import com.sellerprofit.repository.UserRepository;
import com.sellerprofit.subscription.dto.CompGrantResult;
import com.sellerprofit.subscription.dto.PlanView;
import com.sellerprofit.subscription.dto.SubscriptionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    /**
     * 관리자 무상(COMP) 지급(T10.3). 결제(PAID)와 절대 섞이지 않도록 source=COMP 로 저장한다
     * (docs/admin-tasks.md 절대 규칙 4 — 빌링 스케줄러가 COMP 는 결제 시도 없이 만료만 처리, {@code BillingService.renewDue} 참고).
     *
     * 만료일은 <b>연장</b> 방식이다: 이미 유효한 만료일이 있으면(now 이후) 그 위에 N개월을 더하고,
     * 없거나 지났으면 지금부터 N개월로 새로 설정한다 — max(now, 기존 만료일) + N개월.
     *
     * @param userId 지급 대상
     * @param months 지급 개월(1 이상)
     * @return 감사 로그 기록용 지급 전/후 만료일
     */
    @Transactional
    public CompGrantResult grantComp(Long userId, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("months 는 1 이상이어야 합니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));

        OffsetDateTime now = OffsetDateTime.now(KST);
        OffsetDateTime before = user.getCurrentPeriodEnd();
        OffsetDateTime base = (before == null || before.isBefore(now)) ? now : before;
        OffsetDateTime after = base.plusMonths(months);

        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setCurrentPeriodEnd(after);
        user.setSource(SubscriptionSource.COMP);

        return new CompGrantResult(before, after);
    }

    /**
     * 관리자 무상(COMP) 지급 회수(T10.4, 선택 기능). plan 을 FREE 로 강등한다.
     * 결제(PAID) 구독은 여기서 다루지 않는다 — 해지는 {@code BillingService.cancel}(자기 해지)
     * 영역이라, 관리자가 남의 결제 구독을 임의로 끊지 않도록 COMP 만 대상으로 제한한다.
     *
     * <p>{@code currentPeriodEnd} 도 함께 비운다(null) — 즉시 회수 성격상 남은 기간을 인정하지
     * 않기 때문. 이걸 안 비우면 상태는 FREE 인데 예전 만료일이 그대로 남아 관리자 화면과
     * 유저 본인의 구독 화면({@code /api/subscription})에 혼란스러운 값이 계속 노출된다(플랜
     * 게이팅 자체는 {@link PlanType#fromStatus} 가 상태만 보므로 영향 없었지만, 표시값은 버그였음).
     *
     * <p>이미 FREE(= 회수 대상이 없음)인 COMP 유저를 다시 회수하려 하면 400 으로 막는다.
     * source=COMP 인 유저는 항상 ACTIVE(지급중) 아니면 FREE(회수됨/만료됨) 둘 중 하나뿐이라
     * ({@link com.sellerprofit.billing.BillingService} 의 지급/만료 경로 참고), 이 검사 하나로
     * "지금 실제로 회수할 지급이 있는가"를 정확히 가른다. 관리자 화면에서 이미 회수된 계정의
     * 회수 버튼이 계속 눌리는 상태로 남아있던 문제(before=FREE/after=FREE 인 의미없는 감사
     * 로그만 계속 쌓임)의 근본 원인이라, 프론트(회수 버튼 disabled 조건)와 함께 여기서도 막는다.
     *
     * @param userId 회수 대상
     * @return 감사 로그 기록용 회수 전 상태
     */
    @Transactional
    public SubscriptionStatus revokeComp(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));
        if (user.getSource() != SubscriptionSource.COMP) {
            throw new IllegalArgumentException("무상(COMP) 지급 구독만 회수할 수 있습니다.");
        }
        if (user.getSubscriptionStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalArgumentException("이미 회수되었거나 활성 상태가 아니어서 회수할 지급이 없습니다.");
        }
        SubscriptionStatus before = user.getSubscriptionStatus();
        user.setSubscriptionStatus(SubscriptionStatus.FREE);
        user.setCurrentPeriodEnd(null);
        return before;
    }
}
