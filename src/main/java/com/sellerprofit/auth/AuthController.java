package com.sellerprofit.auth;

import com.sellerprofit.auth.dto.AuthUserView;
import com.sellerprofit.auth.dto.SignupRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API.
 *
 * 예) POST /api/auth/signup  {"email":"a@b.com","password":"secret123"} → 무료 가입
 *
 * 로그인/로그아웃(세션)은 Phase 2 에서 추가한다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthUserView signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request.email(), request.password());
    }
}
