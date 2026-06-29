package com.sellerprofit.manage;

import com.sellerprofit.manage.dto.CostRequest;
import com.sellerprofit.manage.dto.CostView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 기타비용(광고비/배송비 등) 입력 API.
 *
 * 예) GET  /api/costs?accountId=1   → 해당 셀러의 비용 목록
 *     POST /api/costs               → 비용 신규 입력
 */
@RestController
@RequestMapping("/api/costs")
public class CostController {

    private final ManagementService managementService;

    public CostController(ManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping
    public List<CostView> list(@RequestParam Long accountId) {
        return managementService.listCosts(accountId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CostView create(@Valid @RequestBody CostRequest request) {
        return managementService.createCost(request);
    }
}
