package com.sellerprofit.ads.dto;

import java.util.List;

/**
 * CSV 업로드 결과. 정상 저장된 신규 건수와, 건너뛴 행의 사유 리포트.
 *
 * <p>멱등: 이미 존재하는(같은 external_ref) 행은 신규가 아니므로 {@code importedCount} 에 세지 않고,
 * 파싱 오류가 아니므로 {@code skipped} 에도 넣지 않는다(재업로드 시 importedCount=0, skipped=[]).</p>
 */
public record ImportResult(
        int importedCount,
        List<SkippedRow> skipped
) {
    /** 건너뛴 행: 파일 내 행 번호(1-based, 헤더 포함)와 사유. */
    public record SkippedRow(int row, String reason) {}
}
