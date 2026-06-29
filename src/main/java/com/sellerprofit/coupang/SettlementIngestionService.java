package com.sellerprofit.coupang;

import com.sellerprofit.coupang.dto.RevenueHistoryItem;
import com.sellerprofit.coupang.dto.RevenueHistoryResponse;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.Product;
import com.sellerprofit.domain.Settlement;
import com.sellerprofit.repository.MarketAccountRepository;
import com.sellerprofit.repository.ProductRepository;
import com.sellerprofit.repository.SettlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 쿠팡 매출(정산) 내역 수집 → 상품 매칭 → 정산 라인 멱등 저장.
 *
 * 트랜잭션 경계는 주문 수집과 동일: HTTP(페이지 조회)는 트랜잭션 밖, 영속화는 페이지 단위.
 * 매출의 진실 원천이므로 실지급액(payout)을 그대로 신뢰한다(수수료 재계산 안 함).
 */
@Service
public class SettlementIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SettlementIngestionService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CoupangApiClient client;
    private final MarketAccountRepository marketAccountRepository;
    private final ProductRepository productRepository;
    private final SettlementRepository settlementRepository;
    private final TransactionTemplate txTemplate;

    public SettlementIngestionService(CoupangApiClient client,
                                      MarketAccountRepository marketAccountRepository,
                                      ProductRepository productRepository,
                                      SettlementRepository settlementRepository,
                                      TransactionTemplate txTemplate) {
        this.client = client;
        this.marketAccountRepository = marketAccountRepository;
        this.productRepository = productRepository;
        this.settlementRepository = settlementRepository;
        this.txTemplate = txTemplate;
    }

    /**
     * 한 마켓 계정의 [from, to] 기간 매출내역을 nextToken 페이징으로 모두 수집한다.
     *
     * @return 새로 저장된 정산 라인 수
     */
    public int ingest(Long accountId, LocalDate from, LocalDate to) {
        MarketAccount account = marketAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("MarketAccount 없음: " + accountId));

        int saved = 0;
        String nextToken = null;
        do {
            RevenueHistoryResponse response = client.fetchRevenueHistory(account, from, to, nextToken);
            List<RevenueHistoryItem> items = response.data();
            if (items != null && !items.isEmpty()) {
                saved += persistPage(accountId, items);
            }
            nextToken = response.nextToken();
        } while (nextToken != null && !nextToken.isBlank());

        markSynced(accountId);
        log.info("정산 수집 완료: accountId={}, 신규 정산 라인={}건", accountId, saved);
        return saved;
    }

    /** 한 페이지를 한 트랜잭션으로 영속화한다. */
    private int persistPage(Long accountId, List<RevenueHistoryItem> items) {
        return txTemplate.execute(status -> {
            MarketAccount account = marketAccountRepository.getReferenceById(accountId);
            int count = 0;
            for (RevenueHistoryItem item : items) {
                if (persistLine(account, accountId, item)) {
                    count++;
                }
            }
            return count;
        });
    }

    /** 정산 라인 1건 멱등 저장. 이미 있으면 건너뛰고 false 를 반환한다. */
    private boolean persistLine(MarketAccount account, Long accountId, RevenueHistoryItem item) {
        String vendorItemId = String.valueOf(item.vendorItemId());
        String externalRef = buildExternalRef(item, vendorItemId);

        if (settlementRepository.existsByMarketAccountIdAndExternalRef(accountId, externalRef)) {
            return false;
        }

        Product product = matchProduct(account, accountId, vendorItemId, item.vendorItemName());

        Settlement settlement = Settlement.create(
                account, product, vendorItemId, externalRef,
                payout(item), item.serviceFee(),
                LocalDate.parse(item.recognitionDate()));
        settlementRepository.save(settlement);
        return true;
    }

    /** 실지급액 = 판매금액 − 판매수수료 (spec 4장). 반품/취소는 쿠팡이 음수로 내려준다. */
    private static BigDecimal payout(RevenueHistoryItem item) {
        BigDecimal sale = item.saleAmount() == null ? BigDecimal.ZERO : item.saleAmount();
        BigDecimal fee = item.serviceFee() == null ? BigDecimal.ZERO : item.serviceFee();
        return sale.subtract(fee);
    }

    /**
     * 멱등 키. 쿠팡이 정산 라인 고유 id 를 주면 그것을 쓰는 게 가장 안전하다.
     * ⚠️ [검증 포인트] 현재는 (인식일+옵션상품+정산유형) 조합으로 구성 →
     *    같은 날 동일 상품·유형이 여러 건이면 충돌해 한 건만 저장될 수 있다.
     *    라이브 응답에 고유 식별자가 있으면 그 필드로 교체할 것.
     */
    private static String buildExternalRef(RevenueHistoryItem item, String vendorItemId) {
        return item.recognitionDate() + ":" + vendorItemId + ":" + item.settlementType();
    }

    /** 기존 상품에 매칭. 없고 상품명이 있으면 생성, 둘 다 없으면 null(스키마상 허용). */
    private Product matchProduct(MarketAccount account, Long accountId,
                                 String vendorItemId, String name) {
        return productRepository.findByMarketAccountIdAndVendorItemId(accountId, vendorItemId)
                .orElseGet(() -> (name == null || name.isBlank())
                        ? null
                        : productRepository.save(Product.create(account, vendorItemId, name)));
    }

    /** 마지막 정산 동기화 시각 갱신(증분 동기화 커서). */
    private void markSynced(Long accountId) {
        txTemplate.executeWithoutResult(status -> {
            MarketAccount account = marketAccountRepository.findById(accountId).orElseThrow();
            account.setLastSettlementSyncedAt(OffsetDateTime.now(KST));
        });
    }
}
