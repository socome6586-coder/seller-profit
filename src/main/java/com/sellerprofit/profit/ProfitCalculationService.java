package com.sellerprofit.profit;

import com.sellerprofit.domain.Cost;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.profit.dto.ProductProfit;
import com.sellerprofit.profit.dto.ProfitSummary;
import com.sellerprofit.repository.CostRepository;
import com.sellerprofit.repository.MarketAccountRepository;
import com.sellerprofit.repository.ProductProfitRow;
import com.sellerprofit.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 상품별 순이익 계산 (제품의 심장).
 *
 *   순이익 = Σ(정산 실수령) − Σ(판매수량 × COGS) − 배분된 기타비용
 *   마진율 = 순이익 / 매출 × 100
 *
 * 정산/원가 집계는 DB(네이티브 쿼리)에서 끝내고, 기타비용 배분만 앱에서 처리한다.
 * 기타비용은 user 단위 기간 총액 → 상품별 '매출 비율'로 배분한다(spec 4장).
 */
@Service
public class ProfitCalculationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;
    private static final int MARGIN_SCALE = 1;

    private final ProductRepository productRepository;
    private final MarketAccountRepository marketAccountRepository;
    private final CostRepository costRepository;

    public ProfitCalculationService(ProductRepository productRepository,
                                    MarketAccountRepository marketAccountRepository,
                                    CostRepository costRepository) {
        this.productRepository = productRepository;
        this.marketAccountRepository = marketAccountRepository;
        this.costRepository = costRepository;
    }

    @Transactional(readOnly = true)
    public ProfitSummary calculate(Long accountId, LocalDate from, LocalDate to) {
        MarketAccount account = marketAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("MarketAccount 없음: " + accountId));
        Long userId = account.getUser().getId();

        List<ProductProfitRow> rows = productRepository.findProfitByPeriod(accountId, from, to);

        // 기간과 겹치는 기타비용 총액 (period_start <= to AND period_end >= from)
        BigDecimal totalCost = costRepository
                .findByUserIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(userId, to, from)
                .stream()
                .map(Cost::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 배분 기준: 양(+)의 매출 합. (반품으로 매출이 음수인 상품엔 비용을 배분하지 않는다.)
        BigDecimal allocationBasis = rows.stream()
                .map(r -> positive(nz(r.getPayout())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ProductProfit> products = new ArrayList<>(rows.size());
        BigDecimal totalAllocated = BigDecimal.ZERO;

        for (ProductProfitRow r : rows) {
            BigDecimal revenue = nz(r.getPayout());
            BigDecimal preProfit = nz(r.getProfit());           // 기타비용 배분 前 순이익

            BigDecimal allocated = allocationBasis.signum() > 0
                    ? totalCost.multiply(positive(revenue))
                        .divide(allocationBasis, MONEY_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            totalAllocated = totalAllocated.add(allocated);

            BigDecimal profit = preProfit.subtract(allocated).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal marginPct = revenue.signum() != 0
                    ? profit.multiply(HUNDRED).divide(revenue, MARGIN_SCALE, RoundingMode.HALF_UP)
                    : null;

            products.add(new ProductProfit(
                    r.getProductId(),
                    r.getName(),
                    money(revenue),
                    r.getUnits() == null ? 0L : r.getUnits(),
                    money(nz(r.getCogsTotal())),
                    money(allocated),
                    profit,
                    marginPct,
                    profit.signum() < 0));
        }

        // 적자 상품이 위로 (핵심 화면 정렬). 배분 後 순이익 기준으로 재정렬.
        products.sort(Comparator.comparing(ProductProfit::profit));

        BigDecimal totalRevenue = money(sum(products, ProductProfit::revenue));
        BigDecimal totalCogs = money(sum(products, ProductProfit::cogsTotal));
        BigDecimal totalProfit = money(sum(products, ProductProfit::profit));
        BigDecimal avgMargin = totalRevenue.signum() != 0
                ? totalProfit.multiply(HUNDRED).divide(totalRevenue, MARGIN_SCALE, RoundingMode.HALF_UP)
                : null;

        return new ProfitSummary(from, to,
                totalRevenue, totalCogs, money(totalAllocated), totalProfit, avgMargin,
                products);
    }

    private static BigDecimal sum(List<ProductProfit> list,
                                  java.util.function.Function<ProductProfit, BigDecimal> field) {
        return list.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal positive(BigDecimal v) {
        return v.signum() > 0 ? v : BigDecimal.ZERO;
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
