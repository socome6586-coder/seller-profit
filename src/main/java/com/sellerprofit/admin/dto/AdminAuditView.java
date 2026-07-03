package com.sellerprofit.admin.dto;

import java.time.OffsetDateTime;
import java.util.Map;

/** T10.5 — 감사 로그 화면용 뷰. {@code detail} 은 저장된 JSONB 를 그대로 펼쳐 보여준다. */
public record AdminAuditView(
        Long id,
        Long adminUserId,
        String action,
        Long targetUserId,
        Map<String, Object> detail,
        OffsetDateTime createdAt) {
}
