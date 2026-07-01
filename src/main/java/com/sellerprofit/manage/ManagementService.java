package com.sellerprofit.manage;

import com.sellerprofit.domain.Cost;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.Product;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.CostType;
import com.sellerprofit.manage.dto.CostRequest;
import com.sellerprofit.manage.dto.CostView;
import com.sellerprofit.manage.dto.ProductView;
import com.sellerprofit.repository.CostRepository;
import com.sellerprofit.repository.MarketAccountRepository;
import com.sellerprofit.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 셀러 직접 입력 도메인(원가/기타비용) 처리.
 *
 * 순이익 계산의 입력값을 사람이 채워 넣는 경로다. 수집(쿠팡 API)이 아니라
 * 사용자가 화면에서 넣는 값이므로, 검증/소유관계 확인을 여기서 책임진다.
 */
@Service
public class ManagementService {

    private final ProductRepository productRepository;
    private final MarketAccountRepository marketAccountRepository;
    private final CostRepository costRepository;

    public ManagementService(ProductRepository productRepository,
                             MarketAccountRepository marketAccountRepository,
                             CostRepository costRepository) {
        this.productRepository = productRepository;
        this.marketAccountRepository = marketAccountRepository;
        this.costRepository = costRepository;
    }

    /** 계정의 상품 목록(원가 입력 화면용). */
    @Transactional(readOnly = true)
    public List<ProductView> listProducts(Long accountId) {
        return productRepository.findByMarketAccountId(accountId).stream()
                .map(ProductView::of)
                .toList();
    }

    /**
     * 상품 매입원가(COGS) 입력/수정.
     *
     * <p>상품→계정→유저 소유가 세션 주체와 다르면 "상품 없음" 으로 처리한다(타 셀러 상품 id 추측 차단).
     */
    @Transactional
    public ProductView updateCogs(Long productId, BigDecimal cogs, Long userId) {
        Product product = productRepository.findById(productId)
                .filter(p -> p.getMarketAccount().getUser().getId().equals(userId))
                .orElseThrow(() -> new IllegalArgumentException("상품 없음: " + productId));
        product.setCogs(cogs);
        return ProductView.of(product);
    }

    /** 계정 소유 셀러의 기타비용 목록. */
    @Transactional(readOnly = true)
    public List<CostView> listCosts(Long accountId) {
        Long userId = ownerId(accountId);
        return costRepository.findByUserIdOrderByPeriodStartDescIdDesc(userId).stream()
                .map(CostView::of)
                .toList();
    }

    /**
     * 기타비용 신규 입력. 기간 역전은 거부한다(스키마 CHECK 와 동일 규칙).
     *
     * ⚠️ 광고비(AD)는 더 이상 여기로 받지 않는다(docs/ad-roi-spec.md §6·§7, docs/DECISIONS.md 참고).
     *    광고비는 SKU 귀속을 위해 {@code /api/ads/spends}(수기) 또는 {@code /api/ads/spends/import}(CSV)
     *    로만 입력한다 — 여기로 들어오면 {@link com.sellerprofit.profit.ProfitCalculationService} 의
     *    기타비용 배분에서 조용히 제외되어(이중차감 방지) 셀러가 입력한 돈이 어디에도 안 잡히는
     *    것처럼 보이는 혼란을 막기 위함.
     */
    @Transactional
    public CostView createCost(CostRequest req) {
        if (req.costType() == CostType.AD) {
            throw new IllegalArgumentException(
                    "광고비는 기타비용이 아니라 광고비 입력(/api/ads/spends)으로 등록해주세요. "
                            + "SKU별 광고 효율을 보려면 그쪽 화면을 이용하세요.");
        }
        if (req.periodEnd().isBefore(req.periodStart())) {
            throw new IllegalArgumentException("기간 종료일이 시작일보다 빠를 수 없습니다.");
        }
        User user = resolveAccount(req.accountId()).getUser();
        Cost cost = costRepository.save(Cost.create(
                user, req.costType(), req.amount(),
                req.periodStart(), req.periodEnd(), req.memo()));
        return CostView.of(cost);
    }

    private Long ownerId(Long accountId) {
        return resolveAccount(accountId).getUser().getId();
    }

    private MarketAccount resolveAccount(Long accountId) {
        return marketAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("MarketAccount 없음: " + accountId));
    }
}
