package com.sellerprofit.admin;

import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관리자 부트스트랩: {@code app.admin-emails} (환경변수 {@code APP_ADMIN_EMAILS}) 에 있는 이메일 계정을
 * ADMIN 으로 승격한다(docs/admin-tasks.md 절대 규칙 2).
 *
 * <p>관리자가 0명이면 아무도 스스로를 승격할 수 없으므로, 코드/DB 가 아닌 설정(시크릿)으로만
 * 최초 관리자를 지정한다. 이메일은 절대 코드/마이그레이션에 하드코딩하지 않는다.
 *
 * <p>승격 시점 두 곳: (1) 앱 기동 시 목록의 이메일이 이미 가입돼 있으면 즉시 승격,
 * (2) 로그인 시 해당 계정이면 즉시 승격(가입은 했지만 아직 서버가 재시작되지 않은 경우 대비).
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

    private final Set<String> adminEmails;
    private final UserRepository userRepository;

    public AdminBootstrapService(@Value("${app.admin-emails:}") String adminEmailsRaw,
                                  UserRepository userRepository) {
        this.adminEmails = parse(adminEmailsRaw);
        this.userRepository = userRepository;
    }

    private static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 이 이메일이 부트스트랩 관리자 목록에 있는지. */
    public boolean isBootstrapAdmin(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase());
    }

    /**
     * user 가 부트스트랩 관리자 이메일이면 ADMIN 으로 승격한다(멱등 — 이미 ADMIN 이면 아무것도 안 함).
     * 저장은 호출측의 트랜잭션(영속성 컨텍스트 dirty checking)에 맡긴다.
     */
    public void promoteIfBootstrapAdmin(User user) {
        if (user.getRole() != Role.ADMIN && isBootstrapAdmin(user.getEmail())) {
            user.setRole(Role.ADMIN);
            log.info("[admin-bootstrap] {} 을(를) ADMIN 으로 승격", user.getEmail());
        }
    }

    /** 기동 시: 목록의 이메일이 이미 가입돼 있으면 즉시 승격(재로그인 없이도 관리자 기능 사용 가능). */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncOnStartup() {
        if (adminEmails.isEmpty()) {
            return;
        }
        for (String email : adminEmails) {
            userRepository.findByEmail(email).ifPresent(this::promoteIfBootstrapAdmin);
        }
    }
}
