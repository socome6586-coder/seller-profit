package com.sellerprofit.account;

import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.User;
import com.sellerprofit.repository.MarketAccountRepository;
import com.sellerprofit.repository.UserRepository;
import com.sellerprofit.subscription.PlanType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마켓 계정 연동/해제. 로그인 유저가 자기 쿠팡 키를 등록하는 입구다(시드 → 실사용 전환).
 *
 * <p>키(accessKey/secretKey)는 평문으로 받아 엔티티 컨버터가 AES-GCM 으로 암호화 저장한다.
 * 플랜 한도(`PlanType.maxMarketAccounts`)를 여기서 강제한다 — FREE 는 1개까지.
 */
@Service
public class AccountConnectionService {

    private final MarketAccountRepository marketAccountRepository;
    private final UserRepository userRepository;

    public AccountConnectionService(MarketAccountRepository marketAccountRepository,
                                    UserRepository userRepository) {
        this.marketAccountRepository = marketAccountRepository;
        this.userRepository = userRepository;
    }

    /**
     * 쿠팡 계정 연동. 플랜 한도 초과/중복 등록은 400 으로 거부한다.
     * (MVP 단일 채널=쿠팡. 채널은 엔티티 기본값으로 COUPANG 고정.)
     */
    @Transactional
    public AccountView connect(Long userId, String vendorId, String accessKey, String secretKey) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));

        PlanType plan = PlanType.fromStatus(user.getSubscriptionStatus());
        int max = plan.maxMarketAccounts();
        long current = marketAccountRepository.countByUserId(userId);
        if (max >= 0 && current >= max) {
            throw new IllegalArgumentException(
                    plan.displayName() + " 플랜은 계정 " + max + "개까지 연동할 수 있습니다. PRO 로 업그레이드하세요.");
        }
        if (marketAccountRepository.existsByUserIdAndVendorId(userId, vendorId)) {
            throw new IllegalArgumentException("이미 연동된 업체코드입니다: " + vendorId);
        }

        MarketAccount saved = marketAccountRepository.save(
                MarketAccount.create(user, vendorId, accessKey, secretKey));
        return AccountView.of(saved);
    }

    /**
     * 계정 연동 해제. 소유가 아니거나 없으면 "없음"(열거 차단).
     * DB FK 가 ON DELETE CASCADE 라 상품/주문/정산/반품도 함께 정리된다.
     */
    @Transactional
    public void disconnect(Long userId, Long accountId) {
        MarketAccount account = marketAccountRepository.findById(accountId)
                .filter(a -> a.getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("MarketAccount 없음: " + accountId));
        marketAccountRepository.delete(account);
    }
}
