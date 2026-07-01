package com.sellerprofit.repository;

import com.sellerprofit.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 수집 시 upsert 판단용
    Optional<Product> findByMarketAccountIdAndVendorItemId(Long marketAccountId, String vendorItemId);

    List<Product> findByMarketAccountId(Long marketAccountId);

    /**
     * 기간별 상품 순이익 집계 (적자 상품이 위로 정렬. 정렬·적자 판정 모두 광고후 순이익 기준).
     * ⚠️ settlements / order_items / return_items / ad_spends 를 한 번에 JOIN 하면 fan-out 으로
     *    합계가 부풀려진다. 각각 CTE 로 먼저 집계한 뒤 LEFT JOIN 한다.
     *
     * COGS 기준 수량 = 주문수량 − 반품수량 (0 미만이면 0). 매출(payout)은 정산이 반품을
     * 음수로 이미 반영하므로 추가 차감하지 않는다(이중 차감 방지).
     *
     * 광고비(ad_spends)는 product_id 가 아니라 vendor_item_id 로만 귀속되므로 별도 CTE(a)로
     * vendor_item_id 기준 집계 후 p.vendor_item_id 로 조인한다. ad_spends 중 vendor_item_id 가
     * 이 계정의 어떤 상품과도 일치하지 않는 몫(미할당분)은 이 쿼리에 나타나지 않는다 —
     * 그 몫은 {@link #sumAdSpendByPeriod} 로 별도 조회해 서비스 계층에서 반영한다.
     */
    @Query(value = """
        WITH s AS (
            SELECT product_id, SUM(payout_amount) AS payout
            FROM settlements
            WHERE market_account_id = :accountId
              AND settled_at BETWEEN :from AND :to
            GROUP BY product_id
        ),
        o AS (
            SELECT product_id, SUM(quantity) AS units
            FROM order_items
            WHERE market_account_id = :accountId
              AND ordered_at::date BETWEEN :from AND :to
            GROUP BY product_id
        ),
        r AS (
            SELECT product_id, SUM(quantity) AS returned
            FROM return_items
            WHERE market_account_id = :accountId
              AND requested_at BETWEEN :from AND :to
            GROUP BY product_id
        ),
        a AS (
            SELECT vendor_item_id, SUM(amount) AS spend
            FROM ad_spends
            WHERE market_account_id = :accountId
              AND spend_date BETWEEN :from AND :to
              AND vendor_item_id IS NOT NULL
            GROUP BY vendor_item_id
        )
        SELECT p.id                                                       AS productId,
               p.name                                                     AS name,
               p.vendor_item_id                                           AS vendorItemId,
               COALESCE(s.payout, 0)                                      AS payout,
               GREATEST(COALESCE(o.units, 0) - COALESCE(r.returned, 0), 0) AS units,
               COALESCE(r.returned, 0)                                    AS returnedUnits,
               GREATEST(COALESCE(o.units, 0) - COALESCE(r.returned, 0), 0)
                   * COALESCE(p.cogs, 0)                                  AS cogsTotal,
               COALESCE(a.spend, 0)                                       AS adSpend,
               COALESCE(s.payout, 0)
                   - GREATEST(COALESCE(o.units, 0) - COALESCE(r.returned, 0), 0)
                       * COALESCE(p.cogs, 0)                              AS profit
        FROM products p
        LEFT JOIN s ON s.product_id = p.id
        LEFT JOIN o ON o.product_id = p.id
        LEFT JOIN r ON r.product_id = p.id
        LEFT JOIN a ON a.vendor_item_id = p.vendor_item_id
        WHERE p.market_account_id = :accountId
        ORDER BY profit ASC
        """, nativeQuery = true)
    List<ProductProfitRow> findProfitByPeriod(@Param("accountId") Long accountId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /**
     * 기간 내 이 계정의 ad_spends 전체 합(vendor_item_id 일치 여부 무관, products 테이블에 대한
     * 의존 없음). {@link #findProfitByPeriod} 가 조인한 "귀속된" 광고비와의 차이가
     * 미할당(unallocated) 광고비다 — vendor_item_id 가 NULL 인 캠페인단위 spend 뿐 아니라,
     * 이 계정의 어떤 상품과도 매칭되지 않는 vendor_item_id(오타/단종 SKU 등)도 여기 포함되어
     * "실비용인데 어디서도 안 잡히는" 누락을 막는다(money-conservation).
     */
    @Query(value = """
        SELECT COALESCE(SUM(amount), 0)
        FROM ad_spends
        WHERE market_account_id = :accountId
          AND spend_date BETWEEN :from AND :to
        """, nativeQuery = true)
    BigDecimal sumAdSpendByPeriod(@Param("accountId") Long accountId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);
}
