package com.sellerprofit.admin;

import com.sellerprofit.admin.dto.AdminAuditView;
import com.sellerprofit.admin.dto.AdminUserView;
import com.sellerprofit.admin.dto.GrantPlanRequest;
import com.sellerprofit.admin.dto.RoleChangeRequest;
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
    private final AdminRoleService adminRoleService;
    private final AdminAuditService adminAuditService;

    public AdminController(AdminAccess adminAccess, AdminUserService adminUserService,
                            AdminGrantService adminGrantService, AdminRoleService adminRoleService,
                            AdminAuditService adminAuditService) {
        this.adminAccess = adminAccess;
        this.adminUserService = adminUserService;
        this.adminGrantService = adminGrantService;
        this.adminRoleService = adminRoleService;
        this.adminAuditService = adminAuditService;
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

    /** 감사 로그(T10.5). 최신순, page/size 페이지네이션(선택, 기본 0/20). */
    @GetMapping("/audit")
    public Page<AdminAuditView> audit(HttpServletRequest http,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size) {
        adminAccess.requireAdmin(http);
        return adminAuditService.list(page, size);
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

    /**
     * COMP 회수(T10.4, 선택). plan 을 FREE 로 강등한다. PAID 구독은 대상이 아니다(400) —
     * 결제 구독 해지는 유저 본인의 {@code /api/billing/cancel} 영역.
     */
    @PostMapping("/users/{id}/revoke")
    public AdminUserView revoke(HttpServletRequest http, @PathVariable Long id) {
        User admin = adminAccess.requireAdmin(http);
        adminGrantService.revokePro(admin.getId(), id);
        return adminUserService.get(id);
    }

    /**
     * Role 변경(T10.4). 마지막 ADMIN 잠금 방지 + 자기 자신의 ADMIN 해제 금지
     * (docs/admin-tasks.md 절대 규칙 3, {@link AdminRoleService} 참고).
     */
    @PostMapping("/users/{id}/role")
    public AdminUserView changeRole(HttpServletRequest http,
                                     @PathVariable Long id,
                                     @Valid @RequestBody RoleChangeRequest request) {
        User admin = adminAccess.requireAdmin(http);
        adminRoleService.changeRole(admin.getId(), id, request.role());
        return adminUserService.get(id);
    }
}
