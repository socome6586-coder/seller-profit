package com.sellerprofit.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 토스페이먼츠 빌링(정기결제) API 클라이언트.
 *
 * <p>인증: 시크릿 키 + ':' 를 Base64 로 인코딩한 HTTP Basic. (토스 규격: 비밀번호는 빈 문자열)
 *
 * <p><b>플레이스홀더 스캐폴딩 단계.</b> 실제 시크릿 키는 환경변수 {@code TOSS_SECRET_KEY} 로 주입한다.
 * 키가 비어 있거나 데모 placeholder 면 {@link #ensureConfigured()} 가 결제 호출을 막아
 * 실수로 미설정 상태에서 외부 호출이 나가지 않게 한다. (키 확보 전까지 안전하게 빌드/배포 가능)
 *
 * @see <a href="https://docs.tosspayments.com/reference">토스페이먼츠 API 레퍼런스</a>
 */
@Component
public class TossBillingClient {

    /** 미설정을 명확히 알리는 데모 기본값. 이 값이면 결제 호출을 차단한다. */
    static final String PLACEHOLDER_SECRET = "test_sk_PLACEHOLDER";

    private final String secretKey;
    private final String baseUrl;
    private final RestClient restClient;

    public TossBillingClient(
            @Value("${toss.billing.secret-key:" + PLACEHOLDER_SECRET + "}") String secretKey,
            @Value("${toss.billing.base-url:https://api.tosspayments.com}") String baseUrl) {
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder().build();
    }

    /** 실 키가 주입됐는지. false 면 결제 기능 비활성(요금 카탈로그/무료 가입은 영향 없음). */
    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank() && !PLACEHOLDER_SECRET.equals(secretKey);
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "토스 시크릿 키(TOSS_SECRET_KEY)가 설정되지 않아 결제를 진행할 수 없습니다.");
        }
    }

    /**
     * 빌링키 발급: 프런트(토스 SDK)가 받은 authKey + customerKey 로 카드 토큰(빌링키)을 교환한다.
     * 발급된 billingKey 는 호출측이 암호화 저장한다.
     *
     * <p>POST /v1/billing/authorizations/issue  {authKey, customerKey} → {billingKey, ...}
     */
    @SuppressWarnings("unchecked")
    public String issueBillingKey(String authKey, String customerKey) {
        ensureConfigured();
        Map<String, Object> body = restClient.post()
                .uri(baseUrl + "/v1/billing/authorizations/issue")
                .headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("authKey", authKey, "customerKey", customerKey))
                .retrieve()
                .body(Map.class);
        Object billingKey = body == null ? null : body.get("billingKey");
        if (billingKey == null) {
            throw new IllegalStateException("토스 빌링키 발급 응답에 billingKey 가 없습니다.");
        }
        return String.valueOf(billingKey);
    }

    /**
     * 빌링키로 결제(청구): 저장해 둔 billingKey 로 금액을 즉시 청구한다(정기결제 1회분).
     * orderId 는 호출측이 주기/회차별로 고유하게 만들어 멱등성(중복청구 방지)을 보장한다.
     *
     * <p>POST /v1/billing/{billingKey}  {customerKey, amount, orderId, orderName}
     *
     * @return 토스 결제 응답(paymentKey/status 등). 실패 시 RestClient 예외가 전파된다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> charge(String billingKey, String customerKey,
                                      int amount, String orderId, String orderName) {
        ensureConfigured();
        return restClient.post()
                .uri(baseUrl + "/v1/billing/" + billingKey)
                .headers(this::auth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "customerKey", customerKey,
                        "amount", amount,
                        "orderId", orderId,
                        "orderName", orderName))
                .retrieve()
                .body(Map.class);
    }

    private void auth(HttpHeaders headers) {
        String token = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + token);
    }
}
