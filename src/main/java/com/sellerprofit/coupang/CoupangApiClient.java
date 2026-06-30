package com.sellerprofit.coupang;

import com.sellerprofit.coupang.dto.OrderSheetResponse;
import com.sellerprofit.coupang.dto.ReturnRequestResponse;
import com.sellerprofit.coupang.dto.RevenueHistoryResponse;
import com.sellerprofit.domain.MarketAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 쿠팡 Open API 호출 클라이언트 (발주서 목록 / 매출내역 조회).
 *
 * ⚠️ 401 방지 핵심: HMAC 서명에 쓰는 query 문자열과 실제 전송 query 가 1바이트라도 달라지면 실패한다.
 *    그래서 query 문자열을 단 한 번 만들어 서명·요청에 동일하게 쓰고,
 *    URI 를 직접 만들어 RestClient 의 재인코딩을 차단한다. (모든 GET 이 {@link #get} 을 공유)
 */
@Component
public class CoupangApiClient {

    private static final String ORDERSHEETS_PATH =
            "/v2/providers/openapi/apis/api/v4/vendors/%s/ordersheets";

    // 매출내역(정산) 조회. ⚠️ 발주서/반품과 달리 vendorId 가 경로가 아니라 쿼리 파라미터다.
    // (이전 경로 .../vendors/{vendorId}/revenue-history 는 404 "No exactly matching API specification" 였음.)
    private static final String REVENUE_HISTORY_PATH =
            "/v2/providers/openapi/apis/api/v1/revenue-history";

    // [검증 포인트] 반품요청 목록 엔드포인트 경로/쿼리 키는 쿠팡 라이브 문서로 최종 확인 필요.
    private static final String RETURN_REQUESTS_PATH =
            "/v2/providers/openapi/apis/api/v4/vendors/%s/returnRequests";

    private final RestClient restClient;
    private final CoupangHmacSigner signer;
    private final String baseUrl;
    private final int maxPerPage;

    public CoupangApiClient(CoupangHmacSigner signer,
                            @Value("${coupang.base-url:https://api-gateway.coupang.com}") String baseUrl,
                            @Value("${coupang.max-per-page:50}") int maxPerPage) {
        this.signer = signer;
        this.baseUrl = baseUrl;
        this.maxPerPage = maxPerPage;
        this.restClient = RestClient.builder().build();
    }

    /**
     * 발주서 목록 한 페이지를 조회한다. nextToken 이 null/blank 면 첫 페이지.
     */
    public OrderSheetResponse fetchOrderSheets(MarketAccount account,
                                               LocalDate from, LocalDate to,
                                               String nextToken) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("createdAtFrom", from.toString());
        params.put("createdAtTo", to.toString());
        params.put("maxPerPage", String.valueOf(maxPerPage));
        if (hasToken(nextToken)) {
            params.put("nextToken", nextToken);
        }
        String path = ORDERSHEETS_PATH.formatted(account.getVendorId());
        return get(account, path, params, OrderSheetResponse.class);
    }

    /**
     * 매출(정산) 내역 한 페이지를 조회한다. nextToken 이 null/blank 면 첫 페이지.
     */
    public RevenueHistoryResponse fetchRevenueHistory(MarketAccount account,
                                                      LocalDate from, LocalDate to,
                                                      String nextToken) {
        // vendorId 는 경로가 아니라 쿼리로 보낸다. 페이징 키는 'token'(응답 봉투는 nextToken 으로 돌려준다).
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vendorId", account.getVendorId());
        params.put("recognitionDateFrom", from.toString());
        params.put("recognitionDateTo", to.toString());
        params.put("maxPerPage", String.valueOf(maxPerPage));
        if (hasToken(nextToken)) {
            params.put("token", nextToken);
        }
        return get(account, REVENUE_HISTORY_PATH, params, RevenueHistoryResponse.class);
    }

    /**
     * 반품요청 목록 한 페이지를 조회한다. nextToken 이 null/blank 면 첫 페이지.
     *
     * [검증 포인트] createdAtFrom/createdAtTo 의 날짜 포맷·status 필수 여부는 라이브 문서로 확인.
     * 발주서 목록과 달리 반품은 접수일(createdAt) 기준으로 거슬러 조회한다.
     */
    public ReturnRequestResponse fetchReturnRequests(MarketAccount account,
                                                     LocalDate from, LocalDate to,
                                                     String nextToken) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("createdAtFrom", from.toString());
        params.put("createdAtTo", to.toString());
        params.put("maxPerPage", String.valueOf(maxPerPage));
        if (hasToken(nextToken)) {
            params.put("nextToken", nextToken);
        }
        String path = RETURN_REQUESTS_PATH.formatted(account.getVendorId());
        return get(account, path, params, ReturnRequestResponse.class);
    }

    /**
     * 공통 GET: query 문자열을 한 번 만들어 서명·요청에 동일하게 쓰고, URI 를 직접 생성한다.
     */
    private <T> T get(MarketAccount account, String path, Map<String, String> params, Class<T> responseType) {
        String query = buildQuery(params);
        String authorization = signer.authorization(
                account.getAccessKey(), account.getSecretKey(), "GET", path, query);

        // URI 를 직접 생성 → RestClient 가 query 를 재인코딩하지 못하게 한다.
        URI uri = URI.create(baseUrl + path + "?" + query);

        return restClient.get()
                .uri(uri)
                .header("Authorization", authorization)
                .header("X-Requested-By", account.getVendorId())
                .header("Content-Type", "application/json;charset=UTF-8")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(responseType);
    }

    /**
     * 서명/요청에 공유되는 query 문자열('?' 제외)을 만든다.
     * 값은 일관되게 UTF-8 URL 인코딩한다(서명·요청 모두 동일 문자열 사용).
     */
    private String buildQuery(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(e.getKey()).append('=').append(encode(e.getValue()));
        }
        return sb.toString();
    }

    private static boolean hasToken(String token) {
        return token != null && !token.isBlank();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
