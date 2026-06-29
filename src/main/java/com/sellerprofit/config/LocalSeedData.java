package com.sellerprofit.config;

import com.sellerprofit.domain.Cost;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.OrderItem;
import com.sellerprofit.domain.Product;
import com.sellerprofit.domain.Settlement;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.CostType;
import com.sellerprofit.repository.CostRepository;
import com.sellerprofit.repository.MarketAccountRepository;
import com.sellerprofit.repository.OrderItemRepository;
import com.sellerprofit.repository.ProductRepository;
import com.sellerprofit.repository.SettlementRepository;
import com.sellerprofit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 로컬 전용 샘플 데이터. 쿠팡 실 키 없이 대시보드를 눈으로 확인하기 위한 시드.
 *
 * 실행: `--spring.profiles.active=seed` (또는 SPRING_PROFILES_ACTIVE=seed).
 * 이미 데이터가 있으면 아무것도 하지 않는다(멱등).
 *
 * 의도된 시나리오: 흑자 2개 + 적자 1개. 대시보드에서 적자 상품이 맨 위로 올라오는지 확인.
 */
@Component
@Profile("seed")
public class LocalSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSeedData.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final MarketAccountRepository marketAccountRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final SettlementRepository settlementRepository;
    private final CostRepository costRepository;

    public LocalSeedData(UserRepository userRepository,
                         MarketAccountRepository marketAccountRepository,
                         ProductRepository productRepository,
                         OrderItemRepository orderItemRepository,
                         SettlementRepository settlementRepository,
                         CostRepository costRepository) {
        this.userRepository = userRepository;
        this.marketAccountRepository = marketAccountRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.settlementRepository = settlementRepository;
        this.costRepository = costRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("[seed] 기존 데이터가 있어 시드를 건너뜀");
            return;
        }

        User user = userRepository.save(User.create("seed@demo.local", "{noop}seed"));
        MarketAccount account = marketAccountRepository.save(
                MarketAccount.create(user, "SEEDVENDOR", "seed-access-key", "seed-secret-key"));

        LocalDate today = LocalDate.now(KST);
        LocalDate settledAt = today.minusDays(1);
        OffsetDateTime orderedAt = OffsetDateTime.now(KST).minusDays(2);

        // (상품명, vendorItemId, COGS단가, 수량, 판매단가, 정산실수령합)
        seedProduct(account, settledAt, orderedAt, "흑자상품 A", "1001",
                bd(3000), 100, bd(8000), bd(800000));   // profit ≈ +500,000
        seedProduct(account, settledAt, orderedAt, "적자상품 B", "1002",
                bd(9000), 50, bd(6000), bd(300000));    // profit ≈ -150,000 (적자!)
        seedProduct(account, settledAt, orderedAt, "흑자상품 C", "1003",
                bd(1500), 30, bd(5000), bd(150000));    // profit ≈ +105,000

        // 기타비용(광고비) — 기간 총액을 매출 비율로 배분
        costRepository.save(Cost.create(user, CostType.AD, bd(50000),
                today.minusDays(7), today, "샘플 광고비"));

        log.info("[seed] 완료 — accountId={} (대시보드: GET /api/dashboard/profit?accountId={})",
                account.getId(), account.getId());
    }

    private void seedProduct(MarketAccount account, LocalDate settledAt, OffsetDateTime orderedAt,
                             String name, String vendorItemId,
                             BigDecimal cogs, int quantity, BigDecimal salePrice,
                             BigDecimal payout) {
        Product product = Product.create(account, vendorItemId, name);
        product.setCogs(cogs);
        productRepository.save(product);

        orderItemRepository.save(OrderItem.create(
                account, product, "seed-order-" + vendorItemId, vendorItemId,
                quantity, salePrice, "DELIVERED", orderedAt));

        settlementRepository.save(Settlement.create(
                account, product, vendorItemId, "seed-settle-" + vendorItemId,
                payout, BigDecimal.ZERO, settledAt));
    }

    private static BigDecimal bd(long v) {
        return BigDecimal.valueOf(v);
    }
}
