package com.sellerprofit.manage;

import com.sellerprofit.account.AccountAccess;
import com.sellerprofit.auth.CurrentUser;
import com.sellerprofit.manage.dto.CogsUpdateRequest;
import com.sellerprofit.manage.dto.ProductView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 상품/원가 관리 API.
 *
 * 예) GET   /api/products?accountId=1            → 상품 목록(원가 포함)
 *     PATCH /api/products/{id}/cogs  {"cogs":3000} → 매입원가 입력/수정
 *
 * ※ 로그인 필수. 목록은 accountId 소유 확인, 원가 수정은 상품→계정 소유를 서비스에서 확인한다.
 */
@RestController
@RequestMapping("/api/products")
public class ProductManagementController {

    private final ManagementService managementService;
    private final CurrentUser currentUser;
    private final AccountAccess accountAccess;

    public ProductManagementController(ManagementService managementService,
                                       CurrentUser currentUser,
                                       AccountAccess accountAccess) {
        this.managementService = managementService;
        this.currentUser = currentUser;
        this.accountAccess = accountAccess;
    }

    @GetMapping
    public List<ProductView> list(@RequestParam Long accountId, HttpServletRequest http) {
        accountAccess.assertOwner(accountId, currentUser.requireUserId(http));
        return managementService.listProducts(accountId);
    }

    @PatchMapping("/{id}/cogs")
    public ProductView updateCogs(@PathVariable Long id,
                                  @Valid @RequestBody CogsUpdateRequest request,
                                  HttpServletRequest http) {
        return managementService.updateCogs(id, request.cogs(), currentUser.requireUserId(http));
    }
}
