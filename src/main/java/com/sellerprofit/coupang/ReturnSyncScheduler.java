package com.sellerprofit.coupang;

import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.domain.type.Channel;
import com.sellerprofit.repository.MarketAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 쿠팡 마켓 계정 전체를 주기적으로 순회하며 반품요청 목록을 수집한다.
 *
 * 반품은 접수 후 상태가 바뀌므로(회수중→완료) 넉넉한 lookback 으로 거슬러 재확인한다.
 * 한 계정의 실패가 나머지 수집을 막지 않도록 계정 단위로 예외를 격리한다.
 */
@Component
public class ReturnSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReturnSyncScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MarketAccountRepository marketAccountRepository;
    private final ReturnIngestionService ingestionService;
    private final int defaultLookbackDays;

    public ReturnSyncScheduler(MarketAccountRepository marketAccountRepository,
                               ReturnIngestionService ingestionService,
                               @Value("${coupang.return-lookback-days:14}") int defaultLookbackDays) {
        this.marketAccountRepository = marketAccountRepository;
        this.ingestionService = ingestionService;
        this.defaultLookbackDays = defaultLookbackDays;
    }

    /** 1시간마다 전체 쿠팡 계정 반품 수집. 초기 지연으로 부팅 직후 폭주를 피한다. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT3M")
    public void syncAllCoupangAccounts() {
        List<MarketAccount> accounts = marketAccountRepository.findAllByChannel(Channel.COUPANG);
        log.info("쿠팡 반품 동기화 시작: 대상 계정={}개", accounts.size());

        LocalDate today = LocalDate.now(KST);
        for (MarketAccount account : accounts) {
            try {
                LocalDate from = resolveFrom(account, today);
                ingestionService.ingest(account.getId(), from, today);
            } catch (Exception e) {
                // API 키 등 민감정보가 찍히지 않도록 메시지만 기록한다.
                log.warn("계정 반품 수집 실패: accountId={}, 원인={}", account.getId(), e.getMessage());
            }
        }
    }

    /** 마지막 반품 동기화 시각이 있으면 그 날짜부터, 없으면 lookback 일 전부터 수집한다. */
    private LocalDate resolveFrom(MarketAccount account, LocalDate today) {
        if (account.getLastReturnSyncedAt() != null) {
            return account.getLastReturnSyncedAt().atZoneSameInstant(KST).toLocalDate();
        }
        return today.minusDays(defaultLookbackDays);
    }
}
