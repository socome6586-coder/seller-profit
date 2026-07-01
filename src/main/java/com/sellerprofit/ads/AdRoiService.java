package com.sellerprofit.ads;

import com.sellerprofit.ads.domain.AdSpendRepository;
import com.sellerprofit.ads.domain.AdSpendVendorItemAggregate;
import com.sellerprofit.ads.dto.AdRoiRow;
import com.sellerprofit.ads.dto.AdRoiSummary;
import com.sellerprofit.profit.ProfitCalculationService;
import com.sellerprofit.profit.dto.ProductProfit;
import com.sellerprofit.profit.dto.ProfitSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SKU 별 광고 효율 집계 (docs/ad-roi-spec.md §8·§9).
 *
 * 기존 순이익 계산({@link ProfitCalculationService}, 이미 광고성 비용을 배분에서 제외한
 * "광고전 기여이익")을 그대로 얹고, 여기에 SKU 별 {@code ad_spends} 합계만 매칭한다.
 * 순이익 산식을 여기서 재구현하지 않음으로써(profit 코어 재사용) 이중 유지보수와
 * 로직 divergence 를 피한다 — CTE fan-out 방지 규율의 애플리케이션 레벨 버전.
 *
 * 어트리뷰션은 v1 규칙대로 직접 귀속만(§9): vendor_item_id 로 매칭 안 되면(또는 캠페인 단위라
 * vendor_item_id 자체가 없으면) "미할당"으로 별도 집계하고 특정 SKU 엔 붙이지 않는다.
 */
@Service
public class AdRoiService {

    private static final int MONEY_SCALE = 2;

    private final ProfitCalculationService profitCalculationService;
    private final AdSpendRepository adSpendRepository;

    public AdRoiService(ProfitCalculationService profitCalculationService,
                        AdSpendRepository adSpendRepository) {
        this.profitCalculationService = profitCalculationService;
        this.adSpendRepository = adSpendRepository;
    }

    @Transactional(readOnly = true)
    public AdRoiSummary calculate(Long accountId, LocalDate from, LocalDate to) {
        ProfitSummary profitSummary = profitCalculationService.calculate(accountId, from, to);

        List<AdSpendVendorItemAggregate> aggregates =
                adSpendRepository.aggregateByVendorItem(accountId, from, to);

        // key 는 vendor_item_id, null 허용(HashMap) = 캠페인 단위 미할당 spend.
        Map<String, BigDecimal> spendByVendorItem = new HashMap<>();
        BigDecimal totalAdSpend = BigDecimal.ZERO;
        for (AdSpendVendorItemAggregate row : aggregates) {
            BigDecimal amount = nz(row.getTotalAmount());
            spendByVendorItem.merge(row.getVendorItemId(), amount, BigDecimal::add);
            totalAdSpend = totalAdSpend.add(amount);
        }

        List<AdRoiRow> rows = new ArrayList<>(profitSummary.products().size());
        BigDecimal matchedAdSpend = BigDecimal.ZERO;
        BigDecimal reviewAdSpend = BigDecimal.ZERO;

        for (ProductProfit p : profitSummary.products()) {
            BigDecimal adSpend = money(spendByVendorItem.getOrDefault(p.vendorItemId(), BigDecimal.ZERO));
            matchedAdSpend = matchedAdSpend.add(adSpend);

            BigDecimal contributionProfit = p.profit(); // 이미 광고성 비용 제외 후 순이익
            BigDecimal postAdProfit = money(contributionProfit.subtract(adSpend));
            boolean adLoss = adSpend.compareTo(contributionProfit) > 0;
            if (adLoss) {
                reviewAdSpend = reviewAdSpend.add(adSpend);
            }
            BigDecimal roas = adSpend.signum() > 0
                    ? p.revenue().divide(adSpend, MONEY_SCALE, RoundingMode.HALF_UP)
                    : null;

            rows.add(new AdRoiRow(p.productId(), p.name(), p.vendorItemId(), p.revenue(),
                    contributionProfit, adSpend, postAdProfit, roas, adLoss));
        }

        // 광고손실 SKU 최상단, 그 안에서는 광고후 순이익이 나쁜 순(오름차순).
        rows.sort(Comparator.comparing(AdRoiRow::adLoss).reversed()
                .thenComparing(AdRoiRow::postAdProfit));

        BigDecimal unassignedAdSpend = money(totalAdSpend.subtract(matchedAdSpend));

        return new AdRoiSummary(from, to, money(totalAdSpend), money(reviewAdSpend),
                unassignedAdSpend, rows);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
