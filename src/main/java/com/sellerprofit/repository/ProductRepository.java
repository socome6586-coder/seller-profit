package com.sellerprofit.repository;

import com.sellerprofit.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 수집 시 upsert 판단용
    Optional<Product> findByMarketAccountIdAndVendorItemId(Long marketAccountId, String vendorItemId);

    List<Product> findByMarketAccountId(Long marketAccountId);

    /**
     * 기간별 상품 순이익 집계 (적자 상품이 위로 정렬).
     * ⚠️ settlements / order_items / return_items 를 한 번에 JOIN 하면 fan-out 으로 합계가
     *    부풀려진다. 각각 CTE 로 먼저 집계한 뒤 LEFT JOIN 한다.
     *
     * COGS 기준 수량 = 주문수량 − 반품수량 (0 미만이면 0). 매출(payout)은 정산이 반품을
     * 음수로 이미 반영하므로 추가 차감하지 않는다(이중 차감 방지).
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
        )
        SELECT p.id                                                       AS productId,
               p.name                                                     AS name,
               COALESCE(s.payout, 0)                                      AS payout,
               GREATEST(COALESCE(o.units, 0) - COALESCE(r.returned, 0), 0) AS units,
               COALESCE(r.returned, 0)                                    AS returnedUnits,
               GREATEST(COALESCE(o.units, 0) - COALESCE(r.returned, 0), 0)
                   * COALESCE(p.cogs, 0)                                  AS cogsTotal,
               COALESCE(s.payout, 0)
                   - GREATEST(COALESCE(o.units, 0) - COALESCE(r.returned, 0), 0)
                       * COALESCE(p.cogs, 0)                              AS profit
        FROM products p
        LEFT JOIN s ON s.product_id = p.id
        LEFT JOIN o ON o.product_id = p.id
        LEFT JOIN r ON r.product_id = p.id
        WHERE p.market_account_id = :accountId
        ORDER BY profit ASC
        """, nativeQuery = true)
    List<ProductProfitRow> findProfitByPeriod(@Param("accountId") Long accountId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
