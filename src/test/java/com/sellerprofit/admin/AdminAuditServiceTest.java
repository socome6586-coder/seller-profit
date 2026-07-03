package com.sellerprofit.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerprofit.admin.dto.AdminAuditView;
import com.sellerprofit.domain.AdminAudit;
import com.sellerprofit.repository.AdminAuditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T10.5 — AdminAuditService.list: 저장된 JSONB detail 을 Map 으로 되돌려 화면에 뿌릴 수 있게 하는지 증명한다.
 */
class AdminAuditServiceTest {

    private final AdminAuditRepository adminAuditRepository = mock(AdminAuditRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AdminAuditService adminAuditService = new AdminAuditService(adminAuditRepository, objectMapper);

    @Test
    void record이_저장한_JSON을_list에서_Map으로_복원() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("months", 3);
        detail.put("plan", "PRO");
        adminAuditService.record(1L, "GRANT_PLAN", 7L, detail);

        // record() 가 직렬화한 JSON 문자열을 그대로 엔티티에 실어 repository 응답을 흉내낸다.
        AdminAudit saved = AdminAudit.of(1L, "GRANT_PLAN", 7L, "{\"months\":3,\"plan\":\"PRO\"}");
        when(adminAuditRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(saved)));

        Page<AdminAuditView> result = adminAuditService.list(0, 20);

        assertEquals(1, result.getTotalElements());
        AdminAuditView view = result.getContent().get(0);
        assertEquals("GRANT_PLAN", view.action());
        assertEquals(7L, view.targetUserId());
        assertEquals(3, view.detail().get("months"));
        assertEquals("PRO", view.detail().get("plan"));
    }

    @Test
    void page_size가_null이면_기본값으로_보정() {
        when(adminAuditRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of()));

        adminAuditService.list(null, null);

        org.mockito.Mockito.verify(adminAuditRepository)
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, 20));
    }
}
