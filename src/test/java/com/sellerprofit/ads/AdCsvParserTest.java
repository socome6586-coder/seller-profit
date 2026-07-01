package com.sellerprofit.ads;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 쿠팡 광고 CSV 파서 검증. 실제 헤더 확정 전까지 컬럼 별칭(AdCsvParser.Columns)을 넓게 잡고 있어,
 * 여기서 그 별칭 매칭·날짜/금액 파싱·오류 행 skip 동작을 고정한다.
 */
class AdCsvParserTest {

    @Test
    void 정상_행은_모두_파싱된다() {
        String csv = """
                광고일자,옵션ID,캠페인,광고그룹,키워드,광고비
                2026-06-01,1001,여름세일,브랜드검색,샌들,10000
                2026-06-02,1002,여름세일,브랜드검색,슬리퍼,5000
                """;
        List<AdCsvParser.RowResult> rows = AdCsvParser.parse(csv);
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).ok());
        assertEquals(LocalDate.of(2026, 6, 1), rows.get(0).data().spendDate());
        assertEquals("1001", rows.get(0).data().vendorItemId());
        assertEquals(0, new java.math.BigDecimal("10000").compareTo(rows.get(0).data().amount()));
    }

    @Test
    void SKU가_비어있으면_미할당으로_파싱된다() {
        String csv = """
                광고일자,옵션ID,캠페인,광고비
                2026-06-01,,여름세일,10000
                """;
        List<AdCsvParser.RowResult> rows = AdCsvParser.parse(csv);
        assertTrue(rows.get(0).ok());
        assertNull(rows.get(0).data().vendorItemId());
    }

    @Test
    void 잘못된_행이_섞이면_정상_행만_파싱되고_나머지는_사유와_함께_skip된다() {
        String csv = """
                광고일자,옵션ID,캠페인,광고비
                2026-06-01,1001,여름세일,10000
                이상한날짜,1002,여름세일,5000
                2026-06-03,1003,여름세일,이상한금액
                2026-06-04,1004,여름세일,-100
                """;
        List<AdCsvParser.RowResult> rows = AdCsvParser.parse(csv);
        assertEquals(4, rows.size());
        assertTrue(rows.get(0).ok());
        assertFalse(rows.get(1).ok());
        assertFalse(rows.get(2).ok());
        assertFalse(rows.get(3).ok()); // 음수 금액 거부(v1은 양수 spend 만)
        assertNotNull(rows.get(1).error());
    }

    @Test
    void 필수_컬럼이_없으면_헤더_오류로_전체를_알린다() {
        String csv = """
                캠페인,광고그룹
                여름세일,브랜드검색
                """;
        List<AdCsvParser.RowResult> rows = AdCsvParser.parse(csv);
        assertEquals(1, rows.size());
        assertFalse(rows.get(0).ok());
    }

    @Test
    void 금액의_통화기호와_천단위_콤마를_허용한다() {
        assertEquals(0, new java.math.BigDecimal("12345").compareTo(AdCsvParser.parseAmount("₩12,345")));
    }

    @Test
    void 헤더_정규화는_대소문자와_공백_언더스코어를_무시한다() {
        assertEquals("vendoritemid", AdCsvParser.normalizeHeader(" Vendor_Item Id "));
    }
}
