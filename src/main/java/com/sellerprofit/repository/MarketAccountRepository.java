package com.sellerprofit.repository;

import com.sellerprofit.domain.MarketAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MarketAccountRepository extends JpaRepository<MarketAccount, Long> {

    List<MarketAccount> findByUserId(Long userId);

    /** 소유권 확인: 해당 계정이 이 유저 소유인지. (세션 주체 기반 접근 통제) */
    boolean existsByIdAndUserId(Long id, Long userId);

    // 스케줄러: 동기화 대상 채널 전체 조회 등에 활용
    List<MarketAccount> findAllByChannel(com.sellerprofit.domain.type.Channel channel);
}
