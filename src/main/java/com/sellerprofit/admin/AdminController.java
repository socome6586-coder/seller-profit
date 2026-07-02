package com.sellerprofit.admin;

import com.sellerprofit.admin.dto.AdminUserView;
import com.sellerprofit.admin.dto.GrantPlanRequest;
import com.sellerprofit.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 전용 API. 모든 메서드는 진입 즉시 {@link AdminAccess#requireAdmin}으로 세션+ADMIN role 을
 * 검사한다 — UI 숨김이 아니라 서버가 강제한다(docs/admin-tasks.md 절대 규칙 1).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAccess adminAccess;
    private final AdminUserService adminUserService;
    private final AdminGrantService adminGrantService;

    public AdminController(AdminAccess adminAccess, AdminUserService adminUserService,
                            AdminGrantService adminGrantService) {
        this.adminAccess = adminAccess;
        this.adminUserService = adminUserService;
        this.adminGrantService = adminGrantService;
    }

    /** 유저 목록. email 로 부분검색(선택), page/size 로 페이지네이션(선택, 기본 0/20). */
    @GetMapping("/users")
    public Page<AdminUserView> users(HttpServletRequest http,
                                      @RequestParam(required = false) String email,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        adminAccess.requireAdmin(http);
        return adminUserService.list(email, page, size);
    }

    /**
     * PRO N개월 무상 지급(T10.3). SubscriptionService 경유(도메인 우회 금지), source=COMP,
     * 만료일은 max(now, 기존 만료일)+N개월로 연장, 감사 로그 1건 남긴다.
     */
    @PostMapping("/users/{id}/grant")
    public AdminUserView grant(HttpServletRequest http,
                                @PathVariable Long id,
                                @Valid @RequestBody GrantPlanRequest request) {
        User admin = adminAccess.requireAdmin(http);
        adminGrantService.grantPro(admin.getId(), id, request.months(), request.plan());
        return adminUserService.get(id);
    }
}
