package com.sellerprofit.manage;

import com.sellerprofit.auth.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 입력/대시보드 API 공통 예외 → 사람이 읽을 수 있는 400 JSON 으로 변환.
 *
 * 기본 동작이면 IllegalArgumentException 은 500 으로 새어 나가 화면에 원인이 안 보인다.
 * 여기서 400 + 메시지로 내려 사용자가 무엇이 잘못됐는지 알 수 있게 한다.
 * (예: 존재하지 않는 accountId → "MarketAccount 없음: 999")
 */
@RestControllerAdvice(basePackages = {
        "com.sellerprofit.manage",
        "com.sellerprofit.profit",
        "com.sellerprofit.auth",
        "com.sellerprofit.subscription",
        "com.sellerprofit.billing",
        "com.sellerprofit.ads"})
public class ApiExceptionHandler {

    /** 잘못된 입력값/존재하지 않는 리소스 등. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** 로그인 안 한 채 보호된 요청 → 401. */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, String>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    /** 기능이 아직 준비 안 됨(예: 토스 결제 키 미설정) → 503. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    /** @Valid 검증 실패 → 필드별 메시지를 모아서 내려준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::describe)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", detail.isBlank() ? "입력값이 올바르지 않습니다." : detail));
    }

    private static String describe(FieldError fe) {
        return fe.getField() + ": " + fe.getDefaultMessage();
    }
}
