package com.sellerprofit.auth;

import com.sellerprofit.account.AccountAccess;
import com.sellerprofit.account.AccountConnectRequest;
import com.sellerprofit.account.AccountConnectionService;
import com.sellerprofit.account.AccountView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 로그인 유저 본인 리소스 API.
 *
 * 예) GET    /api/me/accounts        → 내 마켓 계정 목록(대시보드 계정 선택용)
 *     POST   /api/me/accounts        → 쿠팡 계정 연동(키 등록, 플랜 한도 적용)
 *     DELETE /api/me/accounts/{id}   → 계정 연동 해제
 *
 * ※ 로그인 필수. accountId 를 직접 입력하지 않고 이 목록에서 고르게 해 타 셀러 계정 추측을 막는다.
 */
@RestController
public class MeController {

    private final CurrentUser currentUser;
    private final AccountAccess accountAccess;
    private final AccountConnectionService accountConnectionService;

    public MeController(CurrentUser currentUser,
                        AccountAccess accountAccess,
                        AccountConnectionService accountConnectionService) {
        this.currentUser = currentUser;
        this.accountAccess = accountAccess;
        this.accountConnectionService = accountConnectionService;
    }

    @GetMapping("/api/me/accounts")
    public List<AccountView> myAccounts(HttpServletRequest http) {
        return accountAccess.myAccounts(currentUser.requireUserId(http));
    }

    @PostMapping("/api/me/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView connect(@Valid @RequestBody AccountConnectRequest req, HttpServletRequest http) {
        Long userId = currentUser.requireUserId(http);
        return accountConnectionService.connect(
                userId, req.vendorId().trim(), req.accessKey().trim(), req.secretKey().trim());
    }

    @DeleteMapping("/api/me/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable Long id, HttpServletRequest http) {
        accountConnectionService.disconnect(currentUser.requireUserId(http), id);
    }
}
