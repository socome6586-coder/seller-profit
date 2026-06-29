package com.sellerprofit.repository;

import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /** 정기결제 대상: 해당 상태이면서 청구 주기가 기준 시각 이전(=만료/도래)인 유저. */
    List<User> findBySubscriptionStatusAndCurrentPeriodEndBefore(
            SubscriptionStatus status, OffsetDateTime threshold);
}
