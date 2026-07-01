package com.sellerprofit.ads.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 광고비(ad_spends) 리포지토리. 멱등 저장/조회 지원.
 */
public interface AdSpendRepository extends JpaRepository<AdSpend, Long> {

    /** 멱등 저장용: 이미 같은 (계정, external_ref) 로 저장됐는지. CSV 재업로드 중복 차단. */
    boolean existsByMarketAccountIdAndExternalRef(Long marketAccountId, String externalRef);

    /** 멱등 조회(디버깅/검증). */
    Optional<AdSpend> findByMarketAccountIdAndExternalRef(Long marketAccountId, String externalRef);
}
