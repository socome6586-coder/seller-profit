package com.sellerprofit.admin;

import com.sellerprofit.admin.dto.AdminUserView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AdminController(AdminAccess adminAccess, AdminUserService adminUserService) {
        this.adminAccess = adminAccess;
        this.adminUserService = adminUserService;
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
}
