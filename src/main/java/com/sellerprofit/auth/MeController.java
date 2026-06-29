package com.sellerprofit.auth;

import com.sellerprofit.account.AccountAccess;
import com.sellerprofit.account.AccountView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로그인 유저 본인 리소스 API.
 *
 * 예) GET /api/me/accounts → 내 마켓 계정 목록(대시보드 계정 선택용)
 *
 * ※ 로그인 필수. accountId 를 직접 입력하지 않고 이 목록에서 고르게 해 타 셀러 계정 추측을 막는다.
 */
@RestController
public class MeController {

    private final CurrentUser currentUser;
    private final AccountAccess accountAccess;

    public MeController(CurrentUser currentUser, AccountAccess accountAccess) {
        this.currentUser = currentUser;
        this.accountAccess = accountAccess;
    }

    @GetMapping("/api/me/accounts")
    public List<AccountView> myAccounts(HttpServletRequest http) {
        return accountAccess.myAccounts(currentUser.requireUserId(http));
    }
}
