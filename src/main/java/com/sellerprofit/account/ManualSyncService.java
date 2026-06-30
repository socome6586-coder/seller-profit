package com.sellerprofit.account;

import com.sellerprofit.coupang.OrderIngestionService;
import com.sellerprofit.coupang.ReturnIngestionService;
import com.sellerprofit.coupang.SettlementIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.Callable;

/**
 * 수동 동기화. 연동 직후 스케줄러(30분/1시간 주기)를 기다리지 않고 즉시 한 계정을 수집한다.
 *
 * <p>라이브 키 검증용으로 특히 유용하다 — 버튼 한 번으로 실호출이 나가고, 소스별 결과/오류가
 * 바로 돌아온다. 트랜잭션을 걸지 않는다(각 ingestion 이 HTTP는 트랜잭션 밖에서 처리).
 */
@Service
public class ManualSyncService {

    private static final Logger log = LoggerFactory.getLogger(ManualSyncService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AccountAccess accountAccess;
    private final OrderIngestionService orderIngestion;
    private final SettlementIngestionService settlementIngestion;
    private final ReturnIngestionService returnIngestion;
    private final int lookbackDays;

    public ManualSyncService(AccountAccess accountAccess,
                             OrderIngestionService orderIngestion,
                             SettlementIngestionService settlementIngestion,
                             ReturnIngestionService returnIngestion,
                             @Value("${coupang.manual-sync-lookback-days:14}") int lookbackDays) {
        this.accountAccess = accountAccess;
        this.orderIngestion = orderIngestion;
        this.settlementIngestion = settlementIngestion;
        this.returnIngestion = returnIngestion;
        this.lookbackDays = lookbackDays;
    }

    /**
     * 한 계정 즉시 수집(주문→정산→반품). 소유가 아니면 assertOwner 가 막는다(→400 "없음").
     * 소스별 실패는 격리해 결과로 돌려준다(전체 500 방지).
     */
    public SyncResult syncNow(Long userId, Long accountId) {
        accountAccess.assertOwner(accountId, userId);

        LocalDate today = LocalDate.now(KST);
        LocalDate from = today.minusDays(lookbackDays);
        log.info("수동 동기화 시작: accountId={}, 기간={}~{}", accountId, from, today);

        return new SyncResult(
                run("주문", () -> orderIngestion.ingest(accountId, from, today)),
                run("정산", () -> settlementIngestion.ingest(accountId, from, today)),
                run("반품", () -> returnIngestion.ingest(accountId, from, today)));
    }

    /** 한 소스 수집을 예외 격리해 실행. 키 노출 방지를 위해 메시지만 남긴다. */
    private SyncResult.SourceResult run(String label, Callable<Integer> op) {
        try {
            int count = op.call();
            log.info("수동 동기화 {} 완료: {}건", label, count);
            return SyncResult.SourceResult.ok(count);
        } catch (Exception e) {
            log.warn("수동 동기화 {} 실패: {}", label, e.getMessage());
            return SyncResult.SourceResult.fail(e.getMessage());
        }
    }
}
