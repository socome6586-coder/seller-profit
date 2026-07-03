package com.sellerprofit.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellerprofit.admin.dto.AdminAuditView;
import com.sellerprofit.domain.AdminAudit;
import com.sellerprofit.repository.AdminAuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 관리자 조작 감사 로그 기록(append-only) + 조회(T10.5). docs/admin-tasks.md 절대 규칙 5 —
 * 누가(admin)/언제/누구에게/무엇을(지급 개월·plan, role 변경) 했는지 반드시 남긴다.
 */
@Service
public class AdminAuditService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

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

    /** 최신순 감사 로그 목록(T10.5 화면용). */
    @Transactional(readOnly = true)
    public Page<AdminAuditView> list(Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        return adminAuditRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toView);
    }

    private AdminAuditView toView(AdminAudit audit) {
        Map<String, Object> detail;
        try {
            detail = audit.getDetail() == null
                    ? Map.of()
                    : objectMapper.readValue(audit.getDetail(), new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException e) {
            detail = Map.of("raw", audit.getDetail());
        }
        return new AdminAuditView(audit.getId(), audit.getAdminUserId(), audit.getAction(),
                audit.getTargetUserId(), detail, audit.getCreatedAt());
    }
}
