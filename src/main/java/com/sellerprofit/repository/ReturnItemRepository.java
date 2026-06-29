package com.sellerprofit.repository;

import com.sellerprofit.domain.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    boolean existsByMarketAccountIdAndExternalRef(Long marketAccountId, String externalRef);

    /**
     * 기간 내 반품을 '사유'로 묶어 수량/라인수를 집계한다(많은 순).
     * 사유가 비어 있으면 '미상' 으로 묶는다.
     */
    @Query("""
        SELECT COALESCE(NULLIF(TRIM(r.reason), ''), '미상') AS reason,
               SUM(r.quantity)                            AS quantity,
               COUNT(r)                                   AS lineCount
        FROM ReturnItem r
        WHERE r.marketAccount.id = :accountId
          AND r.requestedAt BETWEEN :from AND :to
        GROUP BY COALESCE(NULLIF(TRIM(r.reason), ''), '미상')
        ORDER BY SUM(r.quantity) DESC
        """)
    List<ReturnReasonRow> aggregateReasonsByPeriod(@Param("accountId") Long accountId,
                                                   @Param("from") LocalDate from,
                                                   @Param("to") LocalDate to);
}
