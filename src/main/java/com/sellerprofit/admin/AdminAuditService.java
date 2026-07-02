package com.sellerprofit.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerprofit.domain.AdminAudit;
import com.sellerprofit.repository.AdminAuditRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 관리자 조작 감사 로그 기록(append-only). docs/admin-tasks.md 절대 규칙 5 —
 * 누가(admin)/언제/누구에게/무엇을(지급 개월·plan, role 변경) 했는지 반드시 남긴다.
 */
@Service
public class AdminAuditService {

    private final AdminAuditRepository adminAuditRepository;
    private final ObjectMapper objectMapper;

    public AdminAuditService(AdminAuditRepository adminAuditRepository, ObjectMapper objectMapper) {
        this.adminAuditRepository = adminAuditRepository;
        this.objectMapper = objectMapper;
    }

    public void record(Long adminUserId, String action, Long targetUserId, Map<String, Object> detail) {
        String json;
        try {
            json = objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("감사 로그 직렬화 실패", e);
        }
        adminAuditRepository.save(AdminAudit.of(adminUserId, action, targetUserId, json));
    }
}
