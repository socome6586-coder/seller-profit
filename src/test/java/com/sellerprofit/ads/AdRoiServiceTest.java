package com.sellerprofit.ads;

import com.sellerprofit.ads.domain.AdSpendRepository;
import com.sellerprofit.ads.domain.AdSpendVendorItemAggregate;
import com.sellerprofit.ads.dto.AdRoiRow;
import com.sellerprofit.ads.dto.AdRoiSummary;
import com.sellerprofit.profit.ProfitCalculationService;
import com.sellerprofit.profit.dto.ProductProfit;
import com.sellerprofit.profit.dto.ProfitSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T4 — 광고 ROI 집계(docs/ad-roi-spec.md §8·§9) 손계산 대조.
 *
 * {@link ProfitCalculationService} 는 목(mock)으로 대체해 "광고전 기여이익"을 고정값으로 주입하고
 * (실제 배분 로직은 T3 {@code ProfitCalculationServiceAdInvariantTest} 가 이미 검증),
 * 여기서는 그 위에 ad_spends 매칭·광고손실 판정·정렬·헤드라인 합산만을 손으로 계산해 대조한다.
 *
 * 픽스처(손계산):
 *   A(1001) 매출 600,000 / 기여이익 450,000, 광고비 20,000 → 광고후 430,000, 손실 아님(20,000 &lt; 450,000)
 *   B(1002) 매출 400,000 / 기여이익  -50,000, 광고비 80,000 → 광고후 -130,000, 손실(80,000 &gt; -50,000)
 *   C(1003) 매출 200,000 / 기여이익   30,000, 광고비 40,000 → 광고후  -10,000, 손실(40,000 &gt;  30,000)
 *   미할당: SKU "9999"(매칭되는 상품 없음) 15,000 + 캠페인단위(SKU 없음) 5,000 = 20,000
 *   totalAdSpend = 20,000+80,000+40,000+15,000+5,000 = 160,000
 *   reviewAdSpend(손실 SKU 광고비 합) = 80,000+40,000 = 120,000
 *   정렬 기대: B(최상단, 광고후순이익 최저) → C → A
 */
class AdRoiServiceTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    @Test
    void 광고손실_SKU가_플래그되고_표_최상단에_정렬된다() {
        AdRoiSummary summary = calculate();

        assertEquals(3, summary.rows().size());
        List<AdRoiRow> rows = summary.rows();
        assertEquals("1002", rows.get(0).vendorItemId());
        assertTrue(rows.get(0).adLoss());
        assertEquals("1003", rows.get(1).vendorItemId());
        assertTrue(rows.get(1).adLoss());
        assertEquals("1001", rows.get(2).vendorItemId());
        assertFalse(rows.get(2).adLoss());
    }

    @Test
    void 미할당_광고비가_별도_버킷으로_정확히_합산된다() {
        AdRoiSummary summary = calculate();

        assertEquals(0, new BigDecimal("160000.00").compareTo(summary.totalAdSpend()));
        assertEquals(0, new BigDecimal("20000.00").compareTo(summary.unassignedAdSpend()));
    }

    @Test
    void 헤드라인_재검토대상_광고비는_광고손실_SKU_광고비_합과_일치한다() {
        AdRoiSummary summary = calculate();

        assertEquals(0, new BigDecimal("120000.00").compareTo(summary.reviewAdSpend()));
    }

    @Test
    void 광고후_순이익과_ROAS가_SKU별로_정확히_계산된다() {
        AdRoiSummary summary = calculate();
        AdRoiRow b = summary.rows().stream().filter(r -> "1002".equals(r.vendorItemId())).findFirst().orElseThrow();

        assertEquals(0, new BigDecimal("-130000.00").compareTo(b.postAdProfit()));
        // ROAS = 귀속매출(400,000) / 광고비(80,000) = 5.00
        assertEquals(0, new BigDecimal("5.00").compareTo(b.roas()));
    }

    private AdRoiSummary calculate() {
        ProfitCalculationService profitCalculationService = mock(ProfitCalculationService.class);
        AdSpendRepository adSpendRepository = mock(AdSpendRepository.class);

        List<ProductProfit> products = List.of(
                productProfit(1L, "상품 A", "1001", "600000", "450000"),
                productProfit(2L, "상품 B", "1002", "400000", "-50000"),
                productProfit(3L, "상품 C", "1003", "200000", "30000"));
        ProfitSummary profitSummary = new ProfitSummary(FROM, TO,
                new BigDecimal("1200000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("430000.00"), null, 0L, products);
        when(profitCalculationService.calculate(ACCOUNT_ID, FROM, TO)).thenReturn(profitSummary);

        // Mockito 는 when(x).thenReturn(y) 사이에 다른 mock()/when() 호출이 끼면(인자로 넘겨받은
        // y 를 만드는 과정에서) 내부 스터빙 상태가 꼬여 UnfinishedStubbingException 을 던진다.
        // 그래서 리스트를 먼저 완성한 뒤에 thenReturn 에 넘긴다.
        List<AdSpendVendorItemAggregate> aggregates = List.of(
                aggregate("1001", "20000"),
                aggregate("1002", "80000"),
                aggregate("1003", "40000"),
                aggregate("9999", "15000"),   // 매칭되는 상품이 없는 SKU → 미할당
                aggregate(null, "5000"));     // 캠페인 단위(SKU 없음) → 미할당
        when(adSpendRepository.aggregateByVendorItem(ACCOUNT_ID, FROM, TO)).thenReturn(aggregates);

        AdRoiService service = new AdRoiService(profitCalculationService, adSpendRepository);
        return service.calculate(ACCOUNT_ID, FROM, TO);
    }

    private static ProductProfit productProfit(Long productId, String name, String vendorItemId,
                                                String revenue, String profit) {
        return new ProductProfit(productId, name, vendorItemId, new BigDecimal(revenue),
                0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(profit), null,
                new BigDecimal(profit).signum() < 0);
    }

    private static AdSpendVendorItemAggregate aggregate(String vendorItemId, String amount) {
        AdSpendVendorItemAggregate a = mock(AdSpendVendorItemAggregate.class);
        when(a.getVendorItemId()).thenReturn(vendorItemId);
        when(a.getTotalAmount()).thenReturn(new BigDecimal(amount));
        return a;
    }
}
