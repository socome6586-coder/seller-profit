package com.sellerprofit.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 아주 단순한 in-memory 고정 윈도우(fixed window) rate limiter.
 *
 * <p>보안 감사(2026-07) 후속 조치 — 로그인/가입/비밀번호 재설정에 무제한 시도가 가능했던 문제를
 * 막기 위한 최소 구현이다. Redis/bucket4j 같은 외부 의존성 없이 컨트롤러 한 곳에서 바로 쓸 수
 * 있게 만들었다.
 *
 * <p><b>한계(의도적 트레이드오프)</b>: 이 서버는 단일 인스턴스로 배포된다(iwinv KR1-Lite,
 * docker-compose.prod.yml 에 app 레플리카 1개). 여러 인스턴스로 수평 확장하게 되면 이 카운터는
 * 인스턴스별로 분리되어 한도가 사실상 인스턴스 수만큼 느슨해지므로, 그 시점엔 Redis 등 공유
 * 저장소 기반으로 교체해야 한다.
 */
@Component
public class RateLimiter {

    private static final class Bucket {
        int count;
        Instant resetAt;
    }

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * key 에 대해 지정된 시간창(window) 동안 limit 회까지만 허용한다. 호출할 때마다 1 회로 센다
     * (성공/실패 결과와 무관하게 시도 자체를 카운트 — 계정 존재 여부 등 부가정보를 새어나가지
     * 않게 하려는 의도).
     *
     * @throws TooManyRequestsException 한도를 초과한 경우
     */
    public synchronized void check(String key, int limit, Duration window) {
        Instant now = Instant.now();
        Bucket b = buckets.computeIfAbsent(key, k -> freshBucket(now, window));
        if (now.isAfter(b.resetAt)) {
            b.count = 0;
            b.resetAt = now.plus(window);
        }
        b.count++;
        if (b.count > limit) {
            throw new TooManyRequestsException("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static Bucket freshBucket(Instant now, Duration window) {
        Bucket b = new Bucket();
        b.count = 0;
        b.resetAt = now.plus(window);
        return b;
    }

    /**
     * 클라이언트 IP 추출. 운영은 Caddy 뒤 단일 홉 프록시 구조라 {@code X-Forwarded-For} 의
     * 마지막 값(=Caddy 가 실제로 관측한 피어 주소)을 신뢰한다 — 첫 번째 값은 클라이언트가 직접
     * 써서 보낼 수 있어 스푸핑 가능하므로 신뢰하지 않는다. 헤더가 없으면(로컬 개발 등) 그냥
     * {@code remoteAddr} 을 쓴다.
     */
    public String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    /** 오래된 버킷을 주기적으로 청소해 메모리가 무한정 늘지 않게 한다(10분마다). */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    void cleanup() {
        Instant now = Instant.now();
        buckets.entrySet().removeIf(e -> now.isAfter(e.getValue().resetAt.plus(Duration.ofMinutes(10))));
    }
}
