package com.sellerprofit.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 정기결제 스케줄러. 매일 한 번 청구주기가 도래한 ACTIVE 구독을 재청구한다.
 *
 * <p>토스 키 미설정 시 {@link BillingService#renewDue}가 부르는 클라이언트가 호출을 막으므로
 * (대상이 없으면 그대로 0건) 키 확보 전에도 안전하게 돌아간다.
 */
@Component
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BillingService billingService;

    public BillingScheduler(BillingService billingService) {
        this.billingService = billingService;
    }

    /** 매일 03:10 KST. 새벽 한산한 시간에 당일 만료분을 청구한다. */
    @Scheduled(cron = "0 10 3 * * *", zone = "Asia/Seoul")
    public void renew() {
        try {
            int charged = billingService.renewDue(OffsetDateTime.now(KST));
            if (charged > 0) {
                log.info("정기결제 배치 완료: {}건 결제", charged);
            }
        } catch (RuntimeException e) {
            log.error("정기결제 배치 오류", e);
        }
    }
}
