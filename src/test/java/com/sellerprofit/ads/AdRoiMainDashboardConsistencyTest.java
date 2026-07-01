package com.sellerprofit.ads;

import com.sellerprofit.ads.domain.AdSpendRepository;
import com.sellerprofit.ads.domain.AdSpendVendorItemAggregate;
import com.sellerprofit.ads.dto.AdRoiRow;
import com.sellerprofit.ads.dto.AdRoiSummary;
import com.sellerprofit.domain.Cost;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.CostType;
import com.sellerprofit.profit.ProfitCalculationService;
import com.sellerprofit.profit.dto.ProductProfit;
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
 * 메인 대시보드("진짜 순이익")와 /ad-roi 화면의 정합성 불변식.
 *
 * <p>두 화면은 서로 다른 쿼리(findProfitByPeriod의 ad_spends CTE vs.
 * {@code AdSpendRepository.aggregateByVendorItem})로 같은 {@code ad_spends} 테이블을 각자
 * 집계하기 때문에, 로직이 갈라져도(divergence) 컴파일은 계속 성공한다 — 이 테스트가 없으면
 * 회귀를 잡을 방법이 없다. 여기서는 {@link ProfitCalculationService} 를 목(mock) 이 아니라
 * 실제 인스턴스로 써서 {@link AdRoiService} 와 정확히 같은 "광고전 기여이익"을 공유하게 하고,
 * 그 위에 광고비만 두 개의 독립된 소스(같은 ad_spends 테이블을 나타내는 두 mock)로 각각
 * 매칭시켜 결과가 항상 같아야 함을 증명한다.
 *
 * <p>고정값(손계산, 반올림 없이 나누어떨어지도록 5:3 비율로 골랐다):
 * <pre>
 *   A(2001) 매출 500,000 / cogsTotal 200,000 → preCostProfit 300,000
 *   B(2002) 매출 300,000 / cogsTotal 150,000 → preCostProfit 150,000
 *   SHIPPING 8,000 을 5:3 배분 → A 5,000 / B 3,000
 *     preAdProfit: A 295,000 / B 147,000
 *   ad_spends: A 20,000 / B 90,000 / 미할당(캠페인단위) 15,000 → 기간 전체 125,000
 *     postAdProfit(main == ad-roi): A 275,000 / B 57,000
 *   메인 총순이익 = (275,000+57,000) − 15,000(미할당) = 317,000
 * </pre>
 */
class AdRoiMainDashboardConsistencyTest {

    private static final Long ACCOUNT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 6, 30);

    @Test
    void 메인_총순이익은_ad_roi_광고후_순이익_합에서_미할당광고비를_뺀_값과_같다() {
        ProfitSummary mainSummary = calculateMainSummary();
        AdRoiSummary adRoiSummary = calculateAdRoi(mainSummary);

        BigDecimal sumPostAdProfit = adRoiSummary.rows().stream()
                .map(AdRoiRow::postAdProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedMainTotal = sumPostAdProfit.subtract(adRoiSummary.unassignedAdSpend());

        assertEquals(0, new BigDecimal("317000.00").compareTo(mainSummary.totalProfit()));
        assertEquals(0, expectedMainTotal.compareTo(mainSummary.totalProfit()));
    }

    @Test
    void 두_화면의_SKU별_순이익과_광고비_총액이_동일하다() {
        ProfitSummary mainSummary = calculateMainSummary();
        AdRoiSummary adRoiSummary = calculateAdRoi(mainSummary);

        for (ProductProfit p : mainSummary.products()) {
            AdRoiRow matching = adRoiSummary.rows().stream()
                    .filter(r -> r.vendorItemId().equals(p.vendorItemId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(0, p.profit().compareTo(matching.postAdProfit()),
                    "SKU " + p.vendorItemId() + " 는 두 화면에서 순이익이 같아야 한다");
        }

        // 두 화면이 각자 독립적으로 집계한 광고비 총액/미할당액도 같은 ad_spends 테이블을
        // 읽는 이상 항상 일치해야 한다.
        assertEquals(0, mainSummary.totalAdSpend().compareTo(adRoiSummary.totalAdSpend()));
        assertEquals(0, mainSummary.unallocatedAdSpend().compareTo(adRoiSummary.unassignedAdSpend()));
    }

    private ProfitSummary calculateMainSummary() {
        ProductRepository productRepository = mock(ProductRepository.class);
        MarketAccountRepository marketAccountRepository = mock(MarketAccountRepository.class);
        CostRepository costRepository = mock(CostRepository.class);

        User user = User.create("seller@example.com", "hash");
        user.setId(USER_ID);
        MarketAccount account = MarketAccount.create(user, "SEEDVENDOR", "ak", "sk");
        account.setId(ACCOUNT_ID);
        when(marketAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        ProductProfitRow rowA = mockRow(1L, "상품 A", "2001", "500000", 0L, 0L, "200000", "300000", "20000");
        ProductProfitRow rowB = mockRow(2L, "상품 B", "2002", "300000", 0L, 0L, "150000", "150000", "90000");
        when(productRepository.findProfitByPeriod(ACCOUNT_ID, FROM, TO))
                .thenReturn(List.of(rowA, rowB));
        // ad_spends 전체 합(귀속 여부 무관) — CTE 로 귀속된 20,000+90,000=110,000 에 미할당
        // 15,000 을 더한 기간 전체 총액. /ad-roi 쪽 aggregateByVendorItem 픽스처와 반드시 같아야
        // "같은 테이블을 보는 두 쿼리"라는 이 테스트의 전제가 성립한다.
        when(productRepository.sumAdSpendByPeriod(ACCOUNT_ID, FROM, TO))
                .thenReturn(new BigDecimal("125000"));

        when(costRepository.findByUserIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        Cost.create(user, CostType.SHIPPING, new BigDecimal("8000"), FROM, TO, "배송비")));

        ProfitCalculationService service = new ProfitCalculationService(
                productRepository, marketAccountRepository, costRepository);
        return service.calculate(ACCOUNT_ID, FROM, TO);
    }

    /**
     * /ad-roi 는 {@link ProfitCalculationService} 를 그대로 재사용한다(로직 재구현 안 함) —
     * 그래서 여기서도 방금 메인 대시보드를 계산한 것과 완전히 같은 accountId/기간으로
     * {@link AdRoiService} 를 만든다. mainSummary 파라미터는 두 계산이 같은 픽스처에서
     * 나온 것임을 테스트 코드 상에서 명시하기 위한 것일 뿐, 값 자체는 쓰지 않는다
     * (AdRoiService 가 내부적으로 ProfitCalculationService.calculate 를 다시 호출한다).
     */
    private AdRoiSummary calculateAdRoi(ProfitSummary mainSummary) {
        // mainSummary 와 완전히 같은 픽스처로 ProfitCalculationService 를 다시 구성해
        // AdRoiService 에 주입한다 — production 코드에서도 AdRoiService 는
        // ProfitCalculationService.calculate 를 직접 호출하므로(재구현 아님) 이게 실제 호출
        // 경로와 동일하다.
        ProductRepository productRepository = mock(ProductRepository.class);
        MarketAccountRepository marketAccountRepository = mock(MarketAccountRepository.class);
        CostRepository costRepository = mock(CostRepository.class);

        User user = User.create("seller@example.com", "hash");
        user.setId(USER_ID);
        MarketAccount account = MarketAccount.create(user, "SEEDVENDOR", "ak", "sk");
        account.setId(ACCOUNT_ID);
        when(marketAccountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(account));

        ProductProfitRow rowA = mockRow(1L, "상품 A", "2001", "500000", 0L, 0L, "200000", "300000", "20000");
        ProductProfitRow rowB = mockRow(2L, "상품 B", "2002", "300000", 0L, 0L, "150000", "150000", "90000");
        when(productRepository.findProfitByPeriod(ACCOUNT_ID, FROM, TO))
                .thenReturn(List.of(rowA, rowB));
        when(productRepository.sumAdSpendByPeriod(ACCOUNT_ID, FROM, TO))
                .thenReturn(new BigDecimal("125000"));

        when(costRepository.findByUserIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(
                        Cost.create(user, CostType.SHIPPING, new BigDecimal("8000"), FROM, TO, "배송비")));

        ProfitCalculationService profitCalculationService = new ProfitCalculationService(
                productRepository, marketAccountRepository, costRepository);

        AdSpendRepository adSpendRepository = mock(AdSpendRepository.class);
        List<AdSpendVendorItemAggregate> aggregates = List.of(
                aggregate("2001", "20000"),
                aggregate("2002", "90000"),
                aggregate(null, "15000"));   // 캠페인 단위(SKU 없음) → 미할당. 총합 125,000 로 CTE 쪽과 일치.
        when(adSpendRepository.aggregateByVendorItem(ACCOUNT_ID, FROM, TO)).thenReturn(aggregates);

        AdRoiService adRoiService = new AdRoiService(profitCalculationService, adSpendRepository);
        return adRoiService.calculate(ACCOUNT_ID, FROM, TO);
    }

    private static ProductProfitRow mockRow(Long productId, String name, String vendorItemId, String payout,
                                            Long units, Long returnedUnits,
                                            String cogsTotal, String profit, String adSpend) {
        ProductProfitRow row = mock(ProductProfitRow.class);
        when(row.getProductId()).thenReturn(productId);
        when(row.getName()).thenReturn(name);
        when(row.getVendorItemId()).thenReturn(vendorItemId);
        when(row.getPayout()).thenReturn(new BigDecimal(payout));
        when(row.getUnits()).thenReturn(units);
        when(row.getReturnedUnits()).thenReturn(returnedUnits);
        when(row.getCogsTotal()).thenReturn(new BigDecimal(cogsTotal));
        when(row.getProfit()).thenReturn(new BigDecimal(profit));
        when(row.getAdSpend()).thenReturn(new BigDecimal(adSpend));
        return row;
    }

    private static AdSpendVendorItemAggregate aggregate(String vendorItemId, String amount) {
        AdSpendVendorItemAggregate a = mock(AdSpendVendorItemAggregate.class);
        when(a.getVendorItemId()).thenReturn(vendorItemId);
        when(a.getTotalAmount()).thenReturn(new BigDecimal(amount));
        return a;
    }
}
