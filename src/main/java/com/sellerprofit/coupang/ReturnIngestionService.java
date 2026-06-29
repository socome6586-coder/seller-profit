package com.sellerprofit.coupang;

import com.sellerprofit.coupang.dto.CoupangReturnItem;
import com.sellerprofit.coupang.dto.ReturnRequest;
import com.sellerprofit.coupang.dto.ReturnRequestResponse;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.Product;
import com.sellerprofit.domain.ReturnItem;
import com.sellerprofit.repository.MarketAccountRepository;
import com.sellerprofit.repository.ProductRepository;
import com.sellerprofit.repository.ReturnItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 쿠팡 반품요청 목록 수집 → 상품 매칭 → 반품 라인 멱등 저장.
 *
 * 트랜잭션 경계는 주문/정산 수집과 동일: HTTP(페이지 조회)는 트랜잭션 밖, 영속화는 페이지 단위.
 * 수집한 반품 수량은 순이익 집계에서 'COGS 기준 수량 = 주문수량 − 반품수량' 보정에 쓰인다.
 */
@Service
public class ReturnIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ReturnIngestionService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final CoupangApiClient client;
    private final MarketAccountRepository marketAccountRepository;
    private final ProductRepository productRepository;
    private final ReturnItemRepository returnItemRepository;
    private final TransactionTemplate txTemplate;

    public ReturnIngestionService(CoupangApiClient client,
                                  MarketAccountRepository marketAccountRepository,
                                  ProductRepository productRepository,
                                  ReturnItemRepository returnItemRepository,
                                  TransactionTemplate txTemplate) {
        this.client = client;
        this.marketAccountRepository = marketAccountRepository;
        this.productRepository = productRepository;
        this.returnItemRepository = returnItemRepository;
        this.txTemplate = txTemplate;
    }

    /**
     * 한 마켓 계정의 [from, to] 기간 반품요청을 nextToken 페이징으로 모두 수집한다.
     *
     * @return 새로 저장된 반품 라인 수
     */
    public int ingest(Long accountId, LocalDate from, LocalDate to) {
        MarketAccount account = marketAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("MarketAccount 없음: " + accountId));

        int saved = 0;
        String nextToken = null;
        do {
            ReturnRequestResponse response = client.fetchReturnRequests(account, from, to, nextToken);
            List<ReturnRequest> requests = response.data();
            if (requests != null && !requests.isEmpty()) {
                saved += persistPage(accountId, requests);
            }
            nextToken = response.nextToken();
        } while (nextToken != null && !nextToken.isBlank());

        markSynced(accountId);
        log.info("반품 수집 완료: accountId={}, 신규 반품 라인={}건", accountId, saved);
        return saved;
    }

    /** 한 페이지(반품요청 여러 건, 각 건의 상품 라인들)를 한 트랜잭션으로 영속화한다. */
    private int persistPage(Long accountId, List<ReturnRequest> requests) {
        return txTemplate.execute(status -> {
            MarketAccount account = marketAccountRepository.getReferenceById(accountId);
            int count = 0;
            for (ReturnRequest request : requests) {
                List<CoupangReturnItem> lines = request.returnItems();
                if (lines == null) {
                    continue;
                }
                // 한 접수번호 안에서 같은 vendorItemId 가 여러 번 나올 수 있으므로
                // 옵션상품별 등장 순번을 매겨 멱등 키 충돌(=반품 수량 누락)을 막는다.
                Map<String, Integer> occurrence = new HashMap<>();
                for (CoupangReturnItem line : lines) {
                    if (line.vendorItemId() == null) {
                        continue;
                    }
                    String vendorItemId = String.valueOf(line.vendorItemId());
                    int ordinal = occurrence.merge(vendorItemId, 1, Integer::sum) - 1;
                    if (persistLine(account, accountId, request, line, vendorItemId, ordinal)) {
                        count++;
                    }
                }
            }
            return count;
        });
    }

    /** 반품 라인 1건 멱등 저장. 이미 있거나 수량이 없으면 건너뛰고 false 를 반환한다. */
    private boolean persistLine(MarketAccount account, Long accountId,
                                ReturnRequest request, CoupangReturnItem line,
                                String vendorItemId, int ordinal) {
        if (line.purchaseCount() == null || line.purchaseCount() <= 0) {
            return false;
        }

        String externalRef = buildExternalRef(request.receiptId(), vendorItemId, ordinal);

        if (returnItemRepository.existsByMarketAccountIdAndExternalRef(accountId, externalRef)) {
            return false;
        }

        Product product = matchProduct(account, accountId, vendorItemId, line.vendorItemName());
        String orderId = request.orderId() == null ? null : String.valueOf(request.orderId());

        ReturnItem returnItem = ReturnItem.create(
                account, product, orderId, vendorItemId, externalRef,
                line.purchaseCount(), request.reason(),
                statusOf(request), requestedDate(request));
        returnItemRepository.save(returnItem);
        return true;
    }

    /**
     * 멱등 키. 쿠팡 반품 접수번호(receiptId)+옵션상품(+등장 순번)으로 구성한다.
     *
     * 한 접수번호에 같은 vendorItemId 라인이 둘 이상 와도 순번(ordinal)으로 구분해
     * 둘째 라인부터 키가 충돌해 누락되는 일을 막는다. 첫 라인(ordinal 0)은 기존 형식
     * (`receiptId:vendorItemId`)을 그대로 유지해 단일 라인 케이스의 키가 바뀌지 않게 한다.
     *
     * ⚠️ [검증 포인트] 쿠팡이 returnItems[] 순서를 호출마다 동일하게 준다는 가정이다.
     *    라인 고유 id 가 응답에 있으면 순번 대신 그 id 를 쓰는 것이 더 안전하다.
     */
    static String buildExternalRef(Object receiptId, String vendorItemId, int ordinal) {
        String base = receiptId + ":" + vendorItemId;
        return ordinal == 0 ? base : base + "#" + ordinal;
    }

    private static String statusOf(ReturnRequest request) {
        return request.receiptStatus() == null ? "UNKNOWN" : request.receiptStatus();
    }

    /**
     * 접수일 추출. createdAt 은 존 정보 없는 `2024-04-08T22:54:46` 또는 `2024-04-08` 형태일 수 있다.
     * 존 없는 시각은 KST 로 간주한다(주문 orderedAt 변환 규칙과 동일).
     */
    private static LocalDate requestedDate(ReturnRequest request) {
        String createdAt = request.createdAt();
        if (createdAt == null || createdAt.isBlank()) {
            return LocalDate.now(KST);
        }
        if (createdAt.length() <= 10) {
            return LocalDate.parse(createdAt.substring(0, 10));
        }
        return LocalDateTime.parse(createdAt).toLocalDate();
    }

    /** 기존 상품에 매칭. 없고 상품명이 있으면 생성, 둘 다 없으면 null(스키마상 허용). */
    private Product matchProduct(MarketAccount account, Long accountId,
                                 String vendorItemId, String name) {
        return productRepository.findByMarketAccountIdAndVendorItemId(accountId, vendorItemId)
                .orElseGet(() -> (name == null || name.isBlank())
                        ? null
                        : productRepository.save(Product.create(account, vendorItemId, name)));
    }

    /** 마지막 반품 동기화 시각 갱신(증분 동기화 커서). */
    private void markSynced(Long accountId) {
        txTemplate.executeWithoutResult(status -> {
            MarketAccount account = marketAccountRepository.findById(accountId).orElseThrow();
            account.setLastReturnSyncedAt(OffsetDateTime.now(KST));
        });
    }
}
