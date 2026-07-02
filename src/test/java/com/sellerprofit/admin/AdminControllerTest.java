package com.sellerprofit.admin;

import com.sellerprofit.admin.dto.AdminUserView;
import com.sellerprofit.auth.UnauthorizedException;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.manage.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

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
@WebMvcTest(AdminController.class)
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
}
