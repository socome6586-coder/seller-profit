package com.sellerprofit.coupang;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 쿠팡 Open API HMAC 인증 헤더 생성.
 *
 * 규칙(공식 스펙):
 *   datetime  = GMT, 포맷 yyMMdd'T'HHmmss'Z'  (예: 240408T225446Z)
 *   message   = datetime + method + path + query   ← query 는 '?' 제외, 전송값과 100% 동일해야 함
 *   signature = HMAC-SHA256(message, secretKey) 의 소문자 hex
 *   header    = "CEA algorithm=HmacSHA256, access-key={accessKey}, signed-date={datetime}, signature={signature}"
 *
 * ⚠️ 401 의 90%는 "서명한 query 와 실제 전송 query 불일치"에서 온다.
 *    반드시 동일한 query 문자열을 서명과 요청에 함께 사용할 것.
 */
@Component
public class CoupangHmacSigner {

    private static final DateTimeFormatter SIGNED_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public String authorization(String accessKey, String secretKey,
                                String method, String path, String query) {
        String datetime = SIGNED_DATE_FORMAT.format(Instant.now());
        String message = datetime + method + path + (query == null ? "" : query);
        String signature = hmacSha256Hex(secretKey, message);

        return "CEA algorithm=HmacSHA256"
                + ", access-key=" + accessKey
                + ", signed-date=" + datetime
                + ", signature=" + signature;
    }

    private String hmacSha256Hex(String secretKey, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);   // 소문자 hex
        } catch (Exception e) {
            throw new IllegalStateException("쿠팡 HMAC 서명 생성 실패", e);
        }
    }
}
