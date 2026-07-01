package com.sellerprofit.ads;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 쿠팡 광고 CSV 파서.
 *
 * <p><b>[검증 포인트]</b> 실제 쿠팡 광고센터 리포트 CSV 의 헤더/날짜포맷/금액표기는 아직 미확정이다.
 * 확정 전까지 컬럼 매핑을 <b>상수(별칭 후보)로 분리</b>해 넓게 잡는다. 실제 파일을 받으면
 * {@link Columns} 별칭과 날짜/금액 파싱만 좁히면 된다(파싱 로직은 그대로).</p>
 *
 * <p>파싱 실패 행은 예외로 중단하지 않고 사유와 함께 건너뛴다(호출측이 리포트).</p>
 */
public final class AdCsvParser {

    private AdCsvParser() {}

    /** 파싱 결과 1행: data 가 있으면 정상, error 가 있으면 건너뜀. line 은 파일 내 1-based 행 번호(헤더 포함). */
    public record RowResult(int line, ParsedRow data, String error) {
        boolean ok() { return error == null; }
    }

    /** 파싱된 한 행의 값(아직 저장 전, 소유/멱등 판단은 서비스가). */
    public record ParsedRow(
            String vendorItemId, String campaign, String adGroup, String keyword,
            LocalDate spendDate, BigDecimal amount) {}

    /**
     * 컬럼 헤더 별칭. [검증 포인트] 실제 헤더 확정 시 여기만 수정.
     * 매칭은 normalizeHeader() 로 소문자화 + 공백/언더스코어 제거 후 비교한다.
     */
    static final class Columns {
        static final Set<String> SPEND_DATE   = Set.of("광고일자","일자","날짜","date","spenddate");
        static final Set<String> VENDOR_ITEM  = Set.of("옵션id","옵션아이디","vendoritemid","노출상품id","상품id","optionid");
        static final Set<String> CAMPAIGN     = Set.of("캠페인","캠페인명","campaign","campaignname");
        static final Set<String> AD_GROUP     = Set.of("광고그룹","광고그룹명","adgroup","adgroupname");
        static final Set<String> KEYWORD      = Set.of("키워드","keyword");
        static final Set<String> AMOUNT       = Set.of("광고비","비용","집행금액","광고비용","spend","cost","amount");
        private Columns() {}
    }

    // [검증 포인트] 실제 리포트 날짜 포맷 확정 시 좁힌다.
    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,               // 2026-06-30
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    };

    /**
     * CSV 텍스트 전체를 파싱한다. 첫 줄은 헤더로 취급하고, 알려진 컬럼을 인덱스로 매핑한다.
     * 필수 컬럼(광고일자·광고비)이 없으면 전체를 헤더 오류로 반환한다(모든 데이터행 skip 없이 명확히).
     */
    public static List<RowResult> parse(String csvText) {
        List<RowResult> out = new ArrayList<>();
        if (csvText == null || csvText.isBlank()) {
            out.add(new RowResult(1, null, "빈 파일"));
            return out;
        }
        String[] lines = csvText.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        // 헤더 매핑
        List<String> header = splitCsvLine(lines[0]);
        Map<String, Integer> idx = new java.util.HashMap<>();
        for (int c = 0; c < header.size(); c++) {
            String h = normalizeHeader(header.get(c));
            put(idx, "spendDate", c, h, Columns.SPEND_DATE);
            put(idx, "vendorItemId", c, h, Columns.VENDOR_ITEM);
            put(idx, "campaign", c, h, Columns.CAMPAIGN);
            put(idx, "adGroup", c, h, Columns.AD_GROUP);
            put(idx, "keyword", c, h, Columns.KEYWORD);
            put(idx, "amount", c, h, Columns.AMOUNT);
        }
        if (!idx.containsKey("spendDate") || !idx.containsKey("amount")) {
            out.add(new RowResult(1, null, "필수 컬럼(광고일자/광고비)을 찾을 수 없습니다. 헤더 확인 필요"));
            return out;
        }

        for (int i = 1; i < lines.length; i++) {
            int lineNo = i + 1; // 1-based, 헤더 포함
            String raw = lines[i];
            if (raw.isBlank()) continue; // 빈 줄은 조용히 무시

            List<String> cells = splitCsvLine(raw);
            try {
                LocalDate date = parseDate(cell(cells, idx.get("spendDate")));
                BigDecimal amount = parseAmount(cell(cells, idx.get("amount")));
                if (amount.signum() < 0) {
                    out.add(new RowResult(lineNo, null, "광고비가 음수입니다(v1은 양수 spend 만)"));
                    continue;
                }
                ParsedRow row = new ParsedRow(
                        blankToNull(cell(cells, idx.get("vendorItemId"))),
                        blankToNull(cell(cells, idx.get("campaign"))),
                        blankToNull(cell(cells, idx.get("adGroup"))),
                        blankToNull(cell(cells, idx.get("keyword"))),
                        date, amount);
                out.add(new RowResult(lineNo, row, null));
            } catch (RuntimeException e) {
                out.add(new RowResult(lineNo, null, e.getMessage()));
            }
        }
        return out;
    }

    private static void put(Map<String, Integer> idx, String key, int col, String normalizedHeader, Set<String> aliases) {
        if (!idx.containsKey(key) && aliases.contains(normalizedHeader)) {
            idx.put(key, col);
        }
    }

    private static String cell(List<String> cells, Integer col) {
        if (col == null || col >= cells.size()) return null;
        return cells.get(col);
    }

    static String normalizeHeader(String h) {
        if (h == null) return "";
        return h.trim().toLowerCase().replaceAll("[\\s_]", "");
    }

    static LocalDate parseDate(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("광고일자 비어있음");
        String s = v.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(s, f);
            } catch (RuntimeException ignored) { /* 다음 포맷 시도 */ }
        }
        throw new IllegalArgumentException("광고일자 형식 오류: " + s);
    }

    static BigDecimal parseAmount(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("광고비 비어있음");
        // 통화기호/천단위 콤마/공백 제거. [검증 포인트] 실제 표기 확정 시 좁힌다.
        String s = v.trim().replaceAll("[₩,\\s]", "");
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("광고비 숫자 오류: " + v.trim());
        }
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 따옴표(")로 감싼 필드 안의 콤마를 보존하는 최소 CSV 라인 파서. */
    static List<String> splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { // 이스케이프된 "
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(ch);
                }
            } else {
                if (ch == '"') {
                    inQuotes = true;
                } else if (ch == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
        }
        fields.add(cur.toString());
        return fields;
    }
}
