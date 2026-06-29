package com.sellerprofit.subscription;

import com.sellerprofit.domain.User;
import com.sellerprofit.repository.UserRepository;
import com.sellerprofit.subscription.dto.PlanView;
import com.sellerprofit.subscription.dto.SubscriptionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * 요금제 카탈로그 + 유저 구독 상태 조회.
 *
 * 결제(토스 빌링) 연동은 Phase 3 에서 붙는다. 여기서는 카탈로그 노출과 현재 상태 조회만 담당한다.
 */
@Service
public class SubscriptionService {

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
}
