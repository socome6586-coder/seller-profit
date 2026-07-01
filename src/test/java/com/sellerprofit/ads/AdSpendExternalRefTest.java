package com.sellerprofit.ads;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 광고비 멱등 키(external_ref) 생성 규칙 검증(명세 §5).
 *
 * 핵심: source:campaign:adGroup:keyword:vendorItemId:spendDate 조합이며,
 * 빈 차원(campaign/adGroup/keyword)·SKU 유무는 고정 토큰("-")으로 대체돼야 재업로드 시
 * 같은 키가 재현된다(= 멱등의 근거).
 */
class AdSpendExternalRefTest {

    private static final LocalDate DATE = LocalDate.of(2026, 6, 30);

    @Test
    void 모든_차원이_채워지면_그대로_조합된다() {
        String ref = AdSpendService.buildExternalRef(
                AdSource.CSV, "여름세일", "브랜드검색", "샌들", "1001", DATE);
        assertEquals("CSV:여름세일:브랜드검색:샌들:1001:2026-06-30", ref);
    }

    @Test
    void 빈_차원은_고정_토큰으로_대체된다() {
        String ref = AdSpendService.buildExternalRef(AdSource.MANUAL, null, "", "  ", null, DATE);
        assertEquals("MANUAL:-:-:-:-:2026-06-30", ref);
    }

    @Test
    void SKU_없는_캠페인단위_spend도_동일_규칙으로_키가_생성된다() {
        String ref = AdSpendService.buildExternalRef(
                AdSource.CSV, "여름세일", null, null, null, DATE);
        assertEquals("CSV:여름세일:-:-:-:2026-06-30", ref);
    }

    @Test
    void 같은_입력이면_재업로드해도_같은_키가_나온다_멱등의_근거() {
        String first = AdSpendService.buildExternalRef(AdSource.CSV, "A", "B", "C", "1001", DATE);
        String second = AdSpendService.buildExternalRef(AdSource.CSV, "A", "B", "C", "1001", DATE);
        assertEquals(first, second);
    }

    @Test
    void 차원이나_SKU가_다르면_키가_다르다() {
        String base = AdSpendService.buildExternalRef(AdSource.CSV, "A", "B", "C", "1001", DATE);
        String diffSku = AdSpendService.buildExternalRef(AdSource.CSV, "A", "B", "C", "1002", DATE);
        String diffCampaign = AdSpendService.buildExternalRef(AdSource.CSV, "Z", "B", "C", "1001", DATE);
        String diffDate = AdSpendService.buildExternalRef(AdSource.CSV, "A", "B", "C", "1001", DATE.plusDays(1));
        assertNotEquals(base, diffSku);
        assertNotEquals(base, diffCampaign);
        assertNotEquals(base, diffDate);
    }

    @Test
    void 토큰_내부의_구분자는_충돌을_피하도록_치환된다() {
        String ref = AdSpendService.buildExternalRef(AdSource.MANUAL, "A:B", null, null, null, DATE);
        assertEquals("MANUAL:A_B:-:-:-:2026-06-30", ref);
    }
}
