package com.sellerprofit.security;

/** {@link RateLimiter} 한도 초과 시 던진다. 컨트롤러 어드바이스가 429 로 변환한다. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
