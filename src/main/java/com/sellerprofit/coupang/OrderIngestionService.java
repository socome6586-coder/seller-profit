package com.sellerprofit.coupang;

import com.sellerprofit.coupang.dto.CoupangOrderItem;
import com.sellerprofit.coupang.dto.OrderSheet;
import com.sellerprofit.coupang.dto.OrderSheetResponse;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.OrderItem;
import com.sellerprofit.domain.Product;
import com.sellerprofit.repository.MarketAccountRepository;
import com.sellerprofit.repository.OrderItemRepository;
import com.sellerprofit.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 쿠팡 발주서 수집 → 상품 upsert → 주문 라인 멱등 저장.
 *
 * 트랜잭션 경계 규칙(중요): HTTP 호출(페이지 조회)은 트랜잭션 밖에서 수행하고,
 * 영속화는 {@link TransactionTemplate} 로 페이지 단위로 짧게 끊는다.
 * (긴 단일 트랜잭션 안에 HTTP 를 넣으면 커넥션을 오래 점유한다.)
 */
@Service
public class OrderIngestionService {

    private static final Logger log = LoggerFactory.getLogger(OrderIngestionService.class);

    // orderedAt 은 존 정보가 없는 로컬 시각 → KST 로 간주해 OffsetDateTime 으로 변환한다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CoupangApiClient client;
    private final MarketAccountRepository marketAccountRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final TransactionTemplate txTemplate;

    public OrderIngestionService(CoupangApiClient client,
                                 MarketAccountRepository marketAccountRepository,
                                 ProductRepository productRepository,
                                 OrderItemRepository orderItemRepository,
                                 TransactionTemplate txTemplate) {
        this.client = client;
        this.marketAccountRepository = marketAccountRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.txTemplate = txTemplate;
    }

    /**
     * 한 마켓 계정의 [from, to] 기간 발주서를 nextToken 페이징으로 모두 수집한다.
     *
     * @return 새로 저장된 주문 라인 수
     */
    public int ingest(Long accountId, LocalDate from, LocalDate to) {
        MarketAccount account = marketAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("MarketAccount 없음: " + accountId));

        int saved = 0;
        String nextToken = null;
        do {
            OrderSheetResponse response = client.fetchOrderSheets(account, from, to, nextToken);
            List<OrderSheet> sheets = response.data();
            if (sheets != null && !sheets.isEmpty()) {
                saved += persistPage(accountId, sheets);
            }
            nextToken = response.nextToken();
        } while (nextToken != null && !nextToken.isBlank());

        markSynced(accountId);
        log.info("발주서 수집 완료: accountId={}, 신규 주문 라인={}건", accountId, saved);
        return saved;
    }

    /** 한 페이지(발주서 목록)를 한 트랜잭션으로 영속화한다. */
    private int persistPage(Long accountId, List<OrderSheet> sheets) {
        return txTemplate.execute(status -> {
            MarketAccount account = marketAccountRepository.getReferenceById(accountId);
            int count = 0;
            for (OrderSheet sheet : sheets) {
                String coupangOrderId = String.valueOf(sheet.orderId());
                OffsetDateTime orderedAt = toKst(sheet.orderedAt());
                if (sheet.orderItems() == null) {
                    continue;
                }
                for (CoupangOrderItem line : sheet.orderItems()) {
                    if (persistLine(account, accountId, coupangOrderId, sheet.status(), orderedAt, line)) {
                        count++;
                    }
                }
            }
            return count;
        });
    }

    /** 주문 라인 1건 멱등 저장. 이미 있으면 건너뛰고 false 를 반환한다. */
    private boolean persistLine(MarketAccount account, Long accountId,
                                String coupangOrderId, String status,
                                OffsetDateTime orderedAt, CoupangOrderItem line) {
        String vendorItemId = String.valueOf(line.vendorItemId());

        if (orderItemRepository.existsByMarketAccountIdAndCoupangOrderIdAndVendorItemId(
                accountId, coupangOrderId, vendorItemId)) {
            return false;
        }

        Product product = upsertProduct(account, accountId, vendorItemId, line.vendorItemName());

        OrderItem orderItem = OrderItem.create(
                account, product, coupangOrderId, vendorItemId,
                line.shippingCount() == null ? 0 : line.shippingCount(),
                line.salesPrice(), status, orderedAt);
        orderItemRepository.save(orderItem);
        return true;
    }

    /** (market_account_id, vendor_item_id) 기준으로 상품을 찾고 없으면 생성한다. */
    private Product upsertProduct(MarketAccount account, Long accountId,
                                  String vendorItemId, String name) {
        return productRepository.findByMarketAccountIdAndVendorItemId(accountId, vendorItemId)
                .orElseGet(() -> productRepository.save(
                        Product.create(account, vendorItemId, name)));
    }

    /** 마지막 주문 동기화 시각 갱신(증분 동기화 커서). */
    private void markSynced(Long accountId) {
        txTemplate.executeWithoutResult(status -> {
            MarketAccount account = marketAccountRepository.findById(accountId).orElseThrow();
            account.setLastOrderSyncedAt(OffsetDateTime.now(KST));
        });
    }

    private static OffsetDateTime toKst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(KST).toOffsetDateTime();
    }
}
