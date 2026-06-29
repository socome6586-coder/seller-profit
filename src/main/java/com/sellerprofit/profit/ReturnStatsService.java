package com.sellerprofit.profit;

import com.sellerprofit.profit.dto.ReturnReasonStat;
import com.sellerprofit.profit.dto.ReturnReasonSummary;
import com.sellerprofit.repository.MarketAccountRepository;
import com.sellerprofit.repository.ReturnReasonRow;
import com.sellerprofit.repository.ReturnItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 반품 사유별 통계 집계.
 *
 * 어떤 사유로 반품이 많이 발생하는지(단순변심/상품불량/배송지연 등) 비중과 함께 보여준다.
 * 수량 집계는 DB(JPQL)에서 끝내고, 비중(%) 계산만 앱에서 처리한다.
 */
@Service
public class ReturnStatsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SHARE_SCALE = 1;

    private final ReturnItemRepository returnItemRepository;
    private final MarketAccountRepository marketAccountRepository;

    public ReturnStatsService(ReturnItemRepository returnItemRepository,
                              MarketAccountRepository marketAccountRepository) {
        this.returnItemRepository = returnItemRepository;
        this.marketAccountRepository = marketAccountRepository;
    }

    @Transactional(readOnly = true)
    public ReturnReasonSummary byReason(Long accountId, LocalDate from, LocalDate to) {
        if (!marketAccountRepository.existsById(accountId)) {
            throw new IllegalArgumentException("MarketAccount 없음: " + accountId);
        }

        List<ReturnReasonRow> rows = returnItemRepository.aggregateReasonsByPeriod(accountId, from, to);

        long total = rows.stream().mapToLong(r -> nz(r.getQuantity())).sum();

        List<ReturnReasonStat> reasons = new ArrayList<>(rows.size());
        for (ReturnReasonRow r : rows) {
            long qty = nz(r.getQuantity());
            BigDecimal share = total > 0
                    ? BigDecimal.valueOf(qty).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(total), SHARE_SCALE, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(SHARE_SCALE, RoundingMode.HALF_UP);
            reasons.add(new ReturnReasonStat(r.getReason(), qty, nz(r.getLineCount()), share));
        }

        return new ReturnReasonSummary(from, to, total, reasons);
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }
}
