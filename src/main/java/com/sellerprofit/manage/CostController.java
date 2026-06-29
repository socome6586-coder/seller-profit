package com.sellerprofit.manage;

import com.sellerprofit.account.AccountAccess;
import com.sellerprofit.auth.CurrentUser;
import com.sellerprofit.manage.dto.CostRequest;
import com.sellerprofit.manage.dto.CostView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 기타비용(광고비/배송비 등) 입력 API.
 *
 * 예) GET  /api/costs?accountId=1   → 해당 셀러의 비용 목록
 *     POST /api/costs               → 비용 신규 입력
 *
 * ※ 로그인 필수. accountId 가 세션 유저 소유인지 확인한 뒤에만 조회/입력한다.
 */
@RestController
@RequestMapping("/api/costs")
public class CostController {

    private final ManagementService managementService;
    private final CurrentUser currentUser;
    private final AccountAccess accountAccess;

    public CostController(ManagementService managementService,
                          CurrentUser currentUser,
                          AccountAccess accountAccess) {
        this.managementService = managementService;
        this.currentUser = currentUser;
        this.accountAccess = accountAccess;
    }

    @GetMapping
    public List<CostView> list(@RequestParam Long accountId, HttpServletRequest http) {
        accountAccess.assertOwner(accountId, currentUser.requireUserId(http));
        return managementService.listCosts(accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CostView create(@Valid @RequestBody CostRequest request, HttpServletRequest http) {
        accountAccess.assertOwner(request.accountId(), currentUser.requireUserId(http));
        return managementService.createCost(request);
    }
}
