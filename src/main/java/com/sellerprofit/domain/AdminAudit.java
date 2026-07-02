package com.sellerprofit.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 관리자 조작 감사 로그(append-only). 누가(adminUserId)/언제(createdAt)/누구에게(targetUserId)/
 * 무엇을(action+detail) 했는지 남긴다. UPDATE/DELETE 는 하지 않는다 — 생성만.
 */
@Entity
@Table(name = "admin_audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    /** GRANT_PLAN | CHANGE_ROLE | REVOKE_PLAN */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    // {months, plan, before, after} 등 액션별 상세. Hibernate 6 내장 JSON 매핑 → DB jsonb.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", columnDefinition = "jsonb")
    private String detail;

    // created_at 은 DB 기본값(now()) 소유 → JPA 는 읽기만
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static AdminAudit of(Long adminUserId, String action, Long targetUserId, String detailJson) {
        AdminAudit a = new AdminAudit();
        a.adminUserId = adminUserId;
        a.action = action;
        a.targetUserId = targetUserId;
        a.detail = detailJson;
        return a;
    }
}
