package com.sellerprofit.admin;

/**
 * 로그인은 했지만 필요한 권한(ADMIN)이 없을 때. ApiExceptionHandler 가 403 으로 변환한다.
 * 메시지에 존재/이유를 노출하지 않는다(기존 계정 열거 차단 스타일과 동일).
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
