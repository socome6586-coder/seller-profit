package com.sellerprofit.billing;

import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.SubscriptionSource;
import com.sellerprofit.domain.type.SubscriptionStatus;
import com.sellerprofit.repository.UserRepository;
import com.sellerprofit.subscription.PlanType;
import com.sellerprofit.subscription.dto.PlanView;
import com.sellerprofit.subscription.dto.SubscriptionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 구독 결제(토스 빌링) 오케스트레이션.
 *
 * <p>흐름:
 * <ol>
 *   <li><b>구독</b>: 프런트가 토스 SDK 로 카드 등록 → authKey 수신 → {@link #subscribe} 가
 *       빌링키 발급·암호화 저장 → 첫 달 즉시 결제 → 상태 ACTIVE + 다음 청구일 설정.</li>
 *   <li><b>정기결제</b>: {@link #renewDue} 가 청구주기 도래한 ACTIVE 유저를 빌링키로 재청구.
 *       성공 시 주기 연장, 실패 시 PAST_DUE.</li>
 *   <li><b>해지</b>: {@link #cancel} 은 상태만 CANCELED 로. 남은 기간까지는 접근 유지(정기결제 대상에서 제외).</li>
 * </ol>
 *
 * <p><b>플레이스홀더 스캐폴딩.</b> 실제 토스 키 미설정 시 {@link TossBillingClient} 가 호출을 막는다.
 * 결제 골격(상태 전이/주기/멱등 orderId)은 완성돼 있고, 키만 주입하면 동작한다.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String ORDER_NAME = "셀러프로핏 PRO 월 구독";

    private final UserRepository userRepository;
    private final TossBillingClient billingClient;

    public BillingService(UserRepository userRepository, TossBillingClient billingClient) {
        this.userRepository = userRepository;
        this.billingClient = billingClient;
    }

    /** 결제 기능 사용 가능 여부(키 설정 여부). 프런트가 '구독' 버튼 노출 판단에 쓸 수 있다. */
    public boolean billingEnabled() {
        return billingClient.isConfigured();
    }

    /**
     * 구독 시작: 카드 등록(authKey) → 빌링키 발급/저장 → 첫 달 결제 → ACTIVE 전환.
     *
     * @param userId  구독할 유저
     * @param authKey 프런트(토스 SDK)가 카드 등록 후 받은 1회용 인증키
     */
    @Transactional
    public SubscriptionView subscribe(Long userId, String authKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));

        // customerKey 는 토스에 회원을 식별시키는 비식별 키. 없으면 1회 생성해 고정한다(PII 금지).
        String customerKey = user.getBillingCustomerKey();
        if (customerKey == null || customerKey.isBlank()) {
            customerKey = "cust_" + UUID.randomUUID();
            user.setBillingCustomerKey(customerKey);
        }

        String billingKey = billingClient.issueBillingKey(authKey, customerKey);
        user.setBillingKey(billingKey);

        OffsetDateTime now = OffsetDateTime.now(KST);
        charge(user, now);                       // 첫 달 즉시 결제
        activateFor(user, now);                  // ACTIVE + 다음 청구일

        return view(user);
    }

    /**
     * 정기결제 배치: 청구주기가 도래/경과한 ACTIVE 유저를 재청구한다.
     * 유저별로 예외를 격리해 한 명의 실패가 배치 전체를 멈추지 않게 한다.
     *
     * <p><b>COMP(무상 지급) 구독은 결제 대상에서 제외한다</b>(docs/admin-tasks.md 빌링 스케줄러 상호작용).
     * 빌링키가 없으므로 결제를 시도하면 안 되고, 만료 처리(FREE 강등)만 PAID 와 동일하게 적용한다.
     *
     * @return 결제 성공 건수(COMP 강등은 포함하지 않는다)
     */
    @Transactional
    public int renewDue(OffsetDateTime now) {
        List<User> due = userRepository.findBySubscriptionStatusAndCurrentPeriodEndBefore(
                SubscriptionStatus.ACTIVE, now);
        int charged = 0;
        for (User user : due) {
            if (user.getSource() == SubscriptionSource.COMP) {
                expireComp(user);   // 결제 시도 없이 만료만 반영
                continue;
            }
            try {
                if (user.getBillingKey() == null || user.getBillingKey().isBlank()) {
                    user.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);   // 빌링키 없음 → 청구 불가
                    continue;
                }
                charge(user, now);
                activateFor(user, now);          // 다음 주기로 연장
                charged++;
            } catch (RuntimeException e) {
                // 결제 실패(한도초과/만료 등) → 연체. 재시도/안내는 추후 정책으로.
                user.setSubscriptionStatus(SubscriptionStatus.PAST_DUE);
                log.warn("정기결제 실패 userId={} → PAST_DUE: {}", user.getId(), e.toString());
            }
        }
        return charged;
    }

    /** COMP 구독 만료 처리: 결제수단(빌링키)이 없으므로 결제 없이 FREE 로 강등만 한다. */
    private void expireComp(User user) {
        user.setSubscriptionStatus(SubscriptionStatus.FREE);
        log.info("COMP 구독 만료 → FREE 강등 userId={}", user.getId());
    }

    /** 해지: 상태만 CANCELED. 남은 기간 접근은 유지하고 다음 청구만 멈춘다. */
    @Transactional
    public SubscriptionView cancel(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));
        user.setSubscriptionStatus(SubscriptionStatus.CANCELED);
        return view(user);
    }

    /** 빌링키로 PRO 1개월분 청구. orderId 는 (customerKey+청구월)로 만들어 중복청구를 막는다. */
    private void charge(User user, OffsetDateTime now) {
        String orderId = user.getBillingCustomerKey() + "-" + now.format(PERIOD);
        Map<String, Object> result = billingClient.charge(
                user.getBillingKey(), user.getBillingCustomerKey(),
                PlanType.PRO.monthlyPrice(), orderId, ORDER_NAME);
        log.info("결제 성공 userId={} orderId={} status={}",
                user.getId(), orderId, result == null ? null : result.get("status"));
    }

    private void activateFor(User user, OffsetDateTime now) {
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setCurrentPeriodEnd(now.plusMonths(1));
        user.setLastBilledAt(now);
    }

    private SubscriptionView view(User user) {
        PlanType plan = PlanType.fromStatus(user.getSubscriptionStatus());
        return new SubscriptionView(
                user.getSubscriptionStatus().name(),
                user.getCurrentPeriodEnd(),
                PlanView.of(plan));
    }
}
