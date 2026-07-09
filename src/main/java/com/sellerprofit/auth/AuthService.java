package com.sellerprofit.auth;

import com.sellerprofit.admin.AdminBootstrapService;
import com.sellerprofit.auth.dto.AuthUserView;
import com.sellerprofit.domain.User;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.domain.type.SubscriptionSource;
import com.sellerprofit.domain.type.SubscriptionStatus;
import com.sellerprofit.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 회원가입 처리. 비밀번호는 BCrypt 해시로만 저장한다(평문 금지).
 *
 * 가입자는 별도 결제 없이 1개월 PRO 무료 지급(COMP)으로 시작한다 — 유저 확보 우선.
 * 로그인/세션은 Phase 2, 유료 전환(토스 빌링)은 Phase 3.
 */
@Service
public class AuthService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapService adminBootstrapService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        AdminBootstrapService adminBootstrapService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminBootstrapService = adminBootstrapService;
    }

    @Transactional
    public AuthUserView signup(String email, String rawPassword, String phone) {
        String normalizedEmail = normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        String normalizedPhone = normalizePhone(phone);
        if (userRepository.existsByPhone(normalizedPhone)) {
            throw new IllegalArgumentException("이미 가입된 휴대전화번호입니다.");
        }
        User user = User.create(normalizedEmail, passwordEncoder.encode(rawPassword), normalizedPhone);
        grantSignupProTrial(user);
        userRepository.save(user);
        adminBootstrapService.promoteIfBootstrapAdmin(user);   // app.admin-emails 계정이면 즉시 ADMIN
        return AuthUserView.of(user);
    }

    private static void grantSignupProTrial(User user) {
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSource(SubscriptionSource.COMP);
        user.setCurrentPeriodEnd(OffsetDateTime.now(KST).plusMonths(1));
    }

    /** 가입 폼의 "중복확인" 버튼용 — 이미 쓰이는 이메일인지 여부만 알려준다(계정 존재 자체는 노출해도
     *  가입 단계에서는 통상적인 UX이므로 로그인 실패 메시지와 달리 문제 없음). */
    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(normalize(email));
    }

    /**
     * 로그인 검증. 성공하면 유저 표현을 돌려준다(세션 저장은 컨트롤러 책임).
     * 실패 사유(미존재/비번불일치)는 구분해 알리지 않는다(계정 존재 여부 노출 방지).
     *
     * 부트스트랩 관리자 승격(app.admin-emails)도 로그인 성공 시점에 함께 반영한다 — 쓰기가
     * 있으므로 readOnly 트랜잭션을 쓰지 않는다.
     */
    @Transactional
    public AuthUserView login(String email, String rawPassword) {
        User user = userRepository.findByEmail(normalize(email))
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));
        adminBootstrapService.promoteIfBootstrapAdmin(user);
        return AuthUserView.of(user);
    }

    /** 세션의 userId 로 현재 유저 조회. */
    @Transactional(readOnly = true)
    public AuthUserView me(Long userId) {
        return userRepository.findById(userId)
                .map(AuthUserView::of)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));
    }

    /**
     * 회원 탈퇴: {@code users} row 를 삭제하면 DB 의 {@code ON DELETE CASCADE} 로 연동 계정
     * (market_accounts)·상품·주문·정산·반품·원가·광고비·비밀번호 재설정 토큰까지 전부 함께
     * 삭제된다(V1~V7 마이그레이션 참고). User 엔티티엔 JPA 연관관계가 없어 이 삭제 자체가
     * DB 레벨 캐스케이드에 온전히 의존한다는 점에 유의.
     *
     * <p>관리자 계정은 이 경로로 탈퇴시키지 않는다 — admin_audit.admin_user_id/target_user_id
     * 는 CASCADE 없이 users 를 참조하므로 감사기록이 있으면 FK 위반이 나고, 마지막 관리자가
     * 실수로 사라지는 사고도 막아야 하기 때문이다. 관리자 본인 탈퇴는 문의처로 안내한다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException(
                    "관리자 계정은 이 화면에서 탈퇴할 수 없습니다. 문의하기로 요청해 주세요.");
        }
        userRepository.delete(user);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    /** 하이픈 등 구분자를 제거해 숫자만 남긴다 — 저장/중복확인 기준을 하나로 통일. */
    private static String normalizePhone(String phone) {
        return phone.replaceAll("[^0-9]", "");
    }
}
