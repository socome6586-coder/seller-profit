package com.sellerprofit.ads.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 광고비(ad_spends) 리포지토리. 멱등 저장/조회 지원.
 */
public interface AdSpendRepository extends JpaRepository<AdSpend, Long> {

    /** 멱등 저장용: 이미 같은 (계정, external_ref) 로 저장됐는지. CSV 재업로드 중복 차단. */
    boolean existsByMarketAccountIdAndExternalRef(Long marketAccountId, String externalRef);

    /** 멱등 조회(디버깅/검증). */
    Optional<AdSpend> findByMarketAccountIdAndExternalRef(Long marketAccountId, String externalRef);

    /**
     * 기간 내 SKU 별 광고비 합계(광고 ROI 집계용). vendorItemId 가 null 인 그룹 = 미할당 spend 합.
     * fan-out 위험 없는 단일 테이블 GROUP BY 라 native CTE 없이 JPQL 로 충분하다.
     */
    @Query("""
            SELECT a.vendorItemId AS vendorItemId, SUM(a.amount) AS totalAmount
            FROM AdSpend a
            WHERE a.marketAccount.id = :accountId
              AND a.spendDate BETWEEN :from AND :to
            GROUP BY a.vendorItemId
            """)
    List<AdSpendVendorItemAggregate> aggregateByVendorItem(@Param("accountId") Long accountId,
                                                            @Param("from") LocalDate from,
                                                            @Param("to") LocalDate to);
}
