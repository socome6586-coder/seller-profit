package com.sellerprofit.manage;

import com.sellerprofit.manage.dto.CogsUpdateRequest;
import com.sellerprofit.manage.dto.ProductView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 상품/원가 관리 API.
 *
 * 예) GET   /api/products?accountId=1            → 상품 목록(원가 포함)
 *     PATCH /api/products/{id}/cogs  {"cogs":3000} → 매입원가 입력/수정
 */
@RestController
@RequestMapping("/api/products")
public class ProductManagementController {

    private final ManagementService managementService;

    public ProductManagementController(ManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping
    public List<ProductView> list(@RequestParam Long accountId) {
        return managementService.listProducts(accountId);
    }

    @PatchMapping("/{id}/cogs")
    public ProductView updateCogs(@PathVariable Long id,
                                  @Valid @RequestBody CogsUpdateRequest request) {
        return managementService.updateCogs(id, request.cogs());
    }
}
