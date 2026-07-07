package com.sellerprofit.admin;

import com.sellerprofit.admin.dto.AdminAuditView;
import com.sellerprofit.admin.dto.AdminUserView;
import com.sellerprofit.auth.UnauthorizedException;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.manage.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T10.1/T10.2 — {@code /api/admin/**} 는 서버가 role 을 강제한다는 것을 HTTP 레벨에서 증명한다.
 * (AdminAccessTest 가 가드 컴포넌트 자체는 이미 단위 검증했고, 여기서는 실제 컨트롤러+
 * ApiExceptionHandler 조합으로 "비관리자 403 / 미로그인 401 / 관리자 200" 을 확인한다.)
 */
// addFilters=false: 이 테스트는 컨트롤러의 role 강제(403/401/200)만 검증한다.
// CsrfFilter(security 패키지) 는 Filter 빈이라 슬라이스 컨텍스트에도 자동 포함되는데, 여기서
// 검증 대상이 아니므로 꺼둔다 — CSRF 자체 동작은 CsrfFilterTest 가 별도로 검증한다.
@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccess adminAccess;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private AdminGrantService adminGrantService;

    @MockitoBean
    private AdminRoleService adminRoleService;

    @MockitoBean
    private AdminAuditService adminAuditService;

    @Test
    void 비관리자는_403() throws Exception {
        doThrow(new ForbiddenException("접근 권한이 없습니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void 미로그인은_401() throws Exception {
        doThrow(new UnauthorizedException("로그인이 필요합니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 관리자는_200_과_목록() throws Exception {
        AdminUserView view = new AdminUserView(
                1L, "user@test.local", OffsetDateTime.now(),
                "USER", "FREE", "FREE", null, "PAID");
        Page<AdminUserView> page = new PageImpl<>(List.of(view));
        when(adminUserService.list(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("user@test.local"))
                .andExpect(jsonPath("$.content[0].role").value("USER"));
    }

    @Test
    void 지급_비관리자는_403() throws Exception {
        doThrow(new ForbiddenException("접근 권한이 없습니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(post("/api/admin/users/1/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"months\":3,\"plan\":\"PRO\"}"))
                .andExpect(status().isForbidden());
        verify(adminGrantService, never()).grantPro(anyLong(), anyLong(), any(), any());
    }

    @Test
    void 지급_미로그인은_401() throws Exception {
        doThrow(new UnauthorizedException("로그인이 필요합니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(post("/api/admin/users/1/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"months\":3,\"plan\":\"PRO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 지급_관리자는_200_과_갱신된_유저정보() throws Exception {
        User admin = User.create("admin@test.local", "hash");
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(adminAccess.requireAdmin(any())).thenReturn(admin);

        AdminUserView granted = new AdminUserView(
                7L, "target@test.local", OffsetDateTime.now(),
                "USER", "PRO", "ACTIVE", OffsetDateTime.now().plusMonths(3), "COMP");
        when(adminUserService.get(7L)).thenReturn(granted);

        mockMvc.perform(post("/api/admin/users/7/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"months\":3,\"plan\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.source").value("COMP"));

        verify(adminGrantService).grantPro(eq(admin.getId()), eq(7L), eq(3), eq("PRO"));
    }

    @Test
    void 지급_months_0이하면_400_컨트롤러_레벨_검증() throws Exception {
        User admin = User.create("admin@test.local", "hash");
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(adminAccess.requireAdmin(any())).thenReturn(admin);

        mockMvc.perform(post("/api/admin/users/7/grant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"months\":0,\"plan\":\"PRO\"}"))
                .andExpect(status().isBadRequest());
        verify(adminGrantService, never()).grantPro(anyLong(), anyLong(), any(), any());
    }

    @Test
    void 회수_비관리자는_403() throws Exception {
        doThrow(new ForbiddenException("접근 권한이 없습니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(post("/api/admin/users/1/revoke"))
                .andExpect(status().isForbidden());
        verify(adminGrantService, never()).revokePro(anyLong(), anyLong());
    }

    @Test
    void 회수_미로그인은_401() throws Exception {
        doThrow(new UnauthorizedException("로그인이 필요합니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(post("/api/admin/users/1/revoke"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 회수_관리자는_200_과_갱신된_유저정보() throws Exception {
        User admin = User.create("admin@test.local", "hash");
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(adminAccess.requireAdmin(any())).thenReturn(admin);

        AdminUserView revoked = new AdminUserView(
                7L, "target@test.local", OffsetDateTime.now(),
                "USER", "FREE", "FREE", null, "COMP");
        when(adminUserService.get(7L)).thenReturn(revoked);

        mockMvc.perform(post("/api/admin/users/7/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("FREE"));

        verify(adminGrantService).revokePro(eq(admin.getId()), eq(7L));
    }

    @Test
    void 회수_PAID_구독은_서비스에서_400_그대로_전파() throws Exception {
        User admin = User.create("admin@test.local", "hash");
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(adminAccess.requireAdmin(any())).thenReturn(admin);

        doThrow(new IllegalArgumentException("무상(COMP) 지급 구독만 회수할 수 있습니다."))
                .when(adminGrantService).revokePro(eq(admin.getId()), eq(7L));

        mockMvc.perform(post("/api/admin/users/7/revoke"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 역할변경_비관리자는_403() throws Exception {
        doThrow(new ForbiddenException("접근 권한이 없습니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(post("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        verify(adminRoleService, never()).changeRole(anyLong(), anyLong(), any());
    }

    @Test
    void 역할변경_미로그인은_401() throws Exception {
        doThrow(new UnauthorizedException("로그인이 필요합니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(post("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 역할변경_관리자는_200_과_갱신된_유저정보() throws Exception {
        User admin = User.create("admin@test.local", "hash");
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(adminAccess.requireAdmin(any())).thenReturn(admin);

        AdminUserView changed = new AdminUserView(
                2L, "target@test.local", OffsetDateTime.now(),
                "ADMIN", "FREE", "FREE", null, "PAID");
        when(adminUserService.get(2L)).thenReturn(changed);

        mockMvc.perform(post("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        verify(adminRoleService).changeRole(eq(admin.getId()), eq(2L), eq("ADMIN"));
    }

    @Test
    void 역할변경_role_빈값이면_400_컨트롤러_레벨_검증() throws Exception {
        User admin = User.create("admin@test.local", "hash");
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(adminAccess.requireAdmin(any())).thenReturn(admin);

        mockMvc.perform(post("/api/admin/users/2/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(adminRoleService, never()).changeRole(anyLong(), anyLong(), any());
    }

    @Test
    void 역할변경_마지막_관리자_강등시도는_서비스에서_400_그대로_전파() throws Exception {
        User admin = User.create("admin@test.local", "hash");
        admin.setId(9L);
        admin.setRole(Role.ADMIN);
        when(adminAccess.requireAdmin(any())).thenReturn(admin);

        doThrow(new IllegalArgumentException("마지막 관리자는 권한을 해제할 수 없습니다."))
                .when(adminRoleService).changeRole(eq(9L), eq(1L), eq("USER"));

        mockMvc.perform(post("/api/admin/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 감사로그_비관리자는_403() throws Exception {
        doThrow(new ForbiddenException("접근 권한이 없습니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 감사로그_미로그인은_401() throws Exception {
        doThrow(new UnauthorizedException("로그인이 필요합니다."))
                .when(adminAccess).requireAdmin(any());

        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 감사로그_관리자는_200_과_목록() throws Exception {
        User admin = User.create("admin@test.local", "hash");
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        when(adminAccess.requireAdmin(any())).thenReturn(admin);

        AdminAuditView view = new AdminAuditView(
                1L, 1L, "GRANT_PLAN", 7L, Map.of("months", 3, "plan", "PRO"), OffsetDateTime.now());
        when(adminAuditService.list(any(), any())).thenReturn(new PageImpl<>(List.of(view)));

        mockMvc.perform(get("/api/admin/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("GRANT_PLAN"))
                .andExpect(jsonPath("$.content[0].targetUserId").value(7));
    }
}
