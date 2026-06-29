package com.sellerprofit.manage;

import com.sellerprofit.domain.Cost;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.Product;
import com.sellerprofit.domain.User;
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

    /** 기타비용 신규 입력. 기간 역전은 거부한다(스키마 CHECK 와 동일 규칙). */
    @Transactional
    public CostView createCost(CostRequest req) {
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
