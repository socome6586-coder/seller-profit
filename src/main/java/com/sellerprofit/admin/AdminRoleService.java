package com.sellerprofit.admin;

import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Role 변경(T10.4). docs/admin-tasks.md 절대 규칙 3 — 마지막 관리자 잠금 방지:
 * (1) 자기 자신의 ADMIN 해제는 항상 불가, (2) 시스템에 ADMIN 이 0명이 되는 변경 불가.
 */
@Service
public class AdminRoleService {

    private final UserRepository userRepository;
    private final AdminAuditService adminAuditService;

    public AdminRoleService(UserRepository userRepository, AdminAuditService adminAuditService) {
        this.userRepository = userRepository;
        this.adminAuditService = adminAuditService;
    }

    @Transactional
    public void changeRole(Long adminUserId, Long targetUserId, String roleValue) {
        Role newRole = parseRole(roleValue);
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + targetUserId));
        Role before = target.getRole();

        if (before == newRole) {
            return; // 실제 변경이 없으면 감사 로그도 남기지 않는다(no-op)
        }
        if (targetUserId.equals(adminUserId) && newRole != Role.ADMIN) {
            throw new IllegalArgumentException("자기 자신의 ADMIN 권한은 해제할 수 없습니다.");
        }
        if (before == Role.ADMIN && newRole != Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new IllegalArgumentException("마지막 관리자는 권한을 해제할 수 없습니다.");
        }

        target.setRole(newRole);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before.name());
        detail.put("after", newRole.name());
        adminAuditService.record(adminUserId, "CHANGE_ROLE", targetUserId, detail);
    }

    private Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("role 은 필수입니다.");
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("role 은 USER 또는 ADMIN 이어야 합니다.");
        }
    }
}
