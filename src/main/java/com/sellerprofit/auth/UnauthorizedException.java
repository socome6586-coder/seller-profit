package com.sellerprofit.auth;

/**
 * 로그인이 필요한 요청에 세션이 없을 때. ApiExceptionHandler 가 401 로 변환한다.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
