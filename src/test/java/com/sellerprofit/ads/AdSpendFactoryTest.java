package com.sellerprofit.ads;

import com.sellerprofit.ads.domain.AdSpend;
import com.sellerprofit.domain.MarketAccount;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * AdSpend 정적 팩토리 필드 매핑 검증(픽스처 기반, 기존 ReturnExternalRefTest 스타일).
 *
 * <p>실제 DB 저장/조회 + Hibernate validate 는 앱 기동(seed 프로파일 + V4 적용)으로 확인한다.
 * 이 테스트는 팩토리가 컬럼과 1:1 로 값을 채우는지(누락/오배치 방지)를 순수 단위로 고정한다.</p>
 */
class AdSpendFactoryTest {

    @Test
    void 팩토리는_모든_필드를_그대로_채운다() {
        MarketAccount account = MarketAccount.create(null, "SEED", "ak", "sk");
        LocalDate date = LocalDate.of(2026, 6, 30);
        BigDecimal amount = new BigDecimal("12345.67");

        AdSpend spend = AdSpend.create(
                account, "1001", "여름세일", "브랜드검색", "샌들",
                date, amount, "CSV", "CSV:여름세일:브랜드검색:샌들:1001:2026-06-30");

        assertSame(account, spend.getMarketAccount());
        assertEquals("1001", spend.getVendorItemId());
        assertEquals("여름세일", spend.getCampaign());
        assertEquals("브랜드검색", spend.getAdGroup());
        assertEquals("샌들", spend.getKeyword());
        assertEquals(date, spend.getSpendDate());
        assertEquals(amount, spend.getAmount());
        assertEquals("CSV", spend.getSource());
        assertEquals("CSV:여름세일:브랜드검색:샌들:1001:2026-06-30", spend.getExternalRef());
    }

    @Test
    void 미할당_spend_는_vendorItemId_와_차원이_null_이어도_생성된다() {
        AdSpend spend = AdSpend.create(
                MarketAccount.create(null, "SEED", "ak", "sk"), null, null, null, null,
                LocalDate.of(2026, 6, 1), new BigDecimal("5000.00"), "MANUAL",
                "MANUAL:-:-:-:-:2026-06-01");

        assertNull(spend.getVendorItemId());
        assertNull(spend.getCampaign());
        assertNull(spend.getAdGroup());
        assertNull(spend.getKeyword());
        assertEquals(new BigDecimal("5000.00"), spend.getAmount());
    }
}
