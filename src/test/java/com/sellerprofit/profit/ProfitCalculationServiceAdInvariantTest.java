package com.sellerprofit.profit;

import com.sellerprofit.domain.Cost;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.CostType;
import com.sellerprofit.profit.dto.ProfitSummary;
import com.sellerprofit.repository.CostRepository;
import com.sellerprofit.repository.MarketAccountRepository;
import com.sellerprofit.repository.ProductProfitRow;
import com.sellerprofit.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T3 — 이중차감 제거 불변식(docs/ad-roi-spec.md §6, docs/ad-roi-tasks.md T3) 증명.
 *
 * <p>순수 Mockito 단위 테스트(Spring 컨텍스트/DB 없음). 두 가지를 고정한다:</p>
 * <ol>
 *   <li>{@link CostType#AD} 비용은 더 이상 기타비용 배분에 섞이지 않는다(이중차감 0).</li>
 *   <li>광고비 X원을 Cost(AD) → ad_spends 로 옮겨도, "배분 후 순이익 합계 − 이전된 광고비"는
 *       옮기기 전(광고비까지 기타비용으로 배분하던 시절)의 순이익 합계와 정확히 같다
 *       (= 전체 순이익 합계 불변, SKU 별 분포만 재배치된다).</li>
 * </ol>
 *
 * <p>고정값은 반올림이 개입하지 않도록(매출 비율이 정확히 나누어떨어지도록) 골랐다 — 60:40 비율.</p>
 */
class ProfitCalculationServiceAdInvariantTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    @Test
    void AD_비용은_기타비용_배분에서_제외되어_이중차감되지_않는다() {
        ProfitSummary summary = calculateWithFixture();

        // 배분 대상은 SHIPPING(20,000) + ETC(10,000) = 30,000 뿐, AD(50,000)는 제외.
        assertEquals(0, new BigDecimal("30000.00").compareTo(summary.totalAllocatedCost()));
    }

    @Test
    void 광고비를_Cost에서_ad_spends로_이전해도_전체_순이익_합계는_불변이다() {
        ProfitSummary summary = calculateWithFixture();
        BigDecimal totalProfitAfter = summary.totalProfit();

        // ad_spends 로 옮겨진 광고비 총액(= 기존 Cost(AD) 금액과 동일해야 멱등 이전).
        BigDecimal totalAdSpend = new BigDecimal("50000.00");

        // 참조값: "옮기기 전"(광고비까지 기타비용으로 배분하던 옛 로직)이라면 나왔을 순이익 합계.
        // totalCost_old = SHIPPING(20,000) + ETC(10,000) + AD(50,000) = 80,000, 60:40 배분.
        //   A: preProfit 450,000 − 48,000 = 402,000
        //   B: preProfit -100,000 − 32,000 = -132,000
        //   합계 = 270,000
        BigDecimal totalProfitOldReference = new BigDecimal("270000.00");

        assertEquals(0, totalProfitOldReference.compareTo(totalProfitAfter.subtract(totalAdSpend)));
    }

    /**
     * 상품 A(매출 600,000, preProfit 450,000) + 상품 B(매출 400,000, preProfit -100,000),
     * 기타비용 SHIPPING 20,000 + ETC 10,000 + AD 50,000 로 계산한 결과.
     */
    private ProfitSummary calculateWithFixture() {
        ProductRepository productRepository = mock(ProductRepository.class);
        MarketAccountRepository marketAccountRepository = mock(MarketAccountRepository.class);
        CostRepository costRepository = mock(CostRepository.class);

        User user = User.create("seller@example.com", "hash");
        user.setId(USER_ID);
        MarketAccount account = MarketAccount.create(user, "SEEDVENDOR", "ak", "sk");
        account.setId(ACCOUNT_ID);
        when(marketAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        ProductProfitRow rowA = mockRow(1L, "상품 A", "600000", 100L, 0L, "150000", "450000");
        ProductProfitRow rowB = mockRow(2L, "상품 B", "400000", 50L, 0L, "500000", "-100000");
        when(productRepository.findProfitByPeriod(ACCOUNT_ID, FROM, TO))
                .thenReturn(List.of(rowA, rowB));

        when(costRepository.findByUserIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        Cost.create(user, CostType.SHIPPING, new BigDecimal("20000"), FROM, TO, "배송비"),
                        Cost.create(user, CostType.ETC, new BigDecimal("10000"), FROM, TO, "기타"),
                        Cost.create(user, CostType.AD, new BigDecimal("50000"), FROM, TO, "광고비(레거시)")));

        ProfitCalculationService service = new ProfitCalculationService(
                productRepository, marketAccountRepository, costRepository);
        return service.calculate(ACCOUNT_ID, FROM, TO);
    }

    private static ProductProfitRow mockRow(Long productId, String name, String payout,
                                            Long units, Long returnedUnits,
                                            String cogsTotal, String profit) {
        ProductProfitRow row = mock(ProductProfitRow.class);
        when(row.getProductId()).thenReturn(productId);
        when(row.getName()).thenReturn(name);
        when(row.getPayout()).thenReturn(new BigDecimal(payout));
        when(row.getUnits()).thenReturn(units);
        when(row.getReturnedUnits()).thenReturn(returnedUnits);
        when(row.getCogsTotal()).thenReturn(new BigDecimal(cogsTotal));
        when(row.getProfit()).thenReturn(new BigDecimal(profit));
        return row;
    }
}
