package com.sellerprofit.coupang;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 반품 멱등 키(external_ref) 생성 규칙 검증.
 *
 * 핵심: 한 접수번호에 같은 옵션상품(vendorItemId) 라인이 둘 이상 와도
 * 서로 다른 키가 나와야 한다(= 반품 수량 누락 방지). 단, 첫 라인은 기존 형식을 유지한다.
 */
class ReturnExternalRefTest {

    @Test
    void 첫_라인은_기존_형식을_유지한다() {
        assertEquals("123:1001", ReturnIngestionService.buildExternalRef(123L, "1001", 0));
    }

    @Test
    void 둘째_이후_라인은_순번이_붙는다() {
        assertEquals("123:1001#1", ReturnIngestionService.buildExternalRef(123L, "1001", 1));
        assertEquals("123:1001#2", ReturnIngestionService.buildExternalRef(123L, "1001", 2));
    }

    @Test
    void 같은_접수번호_같은_상품이라도_순번이_다르면_키가_다르다() {
        String first = ReturnIngestionService.buildExternalRef(777L, "2002", 0);
        String second = ReturnIngestionService.buildExternalRef(777L, "2002", 1);
        assertNotEquals(first, second);
    }

    @Test
    void 접수번호나_상품이_다르면_키가_다르다() {
        String a = ReturnIngestionService.buildExternalRef(1L, "1001", 0);
        String b = ReturnIngestionService.buildExternalRef(2L, "1001", 0);
        String c = ReturnIngestionService.buildExternalRef(1L, "1002", 0);
        assertNotEquals(a, b);
        assertNotEquals(a, c);
    }
}
