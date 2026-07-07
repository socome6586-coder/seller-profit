package com.sellerprofit.auth;

import com.sellerprofit.domain.PasswordResetToken;
import com.sellerprofit.domain.User;
import com.sellerprofit.email.EmailService;
import com.sellerprofit.repository.PasswordResetTokenRepository;
import com.sellerprofit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * 비밀번호 재설정. 토큰은 30분 유효, 1회용.
 *
 * <p>계정 열거 방지: {@link #requestReset} 은 이메일 존재 여부와 무관하게 항상 조용히 끝난다
 * (컨트롤러는 항상 204를 내려 "가입된 이메일인지 아닌지"를 응답으로 알 수 없게 한다).
 */
@Service
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String frontendBaseUrl;

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository tokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 EmailService emailService,
                                 @Value("${app.frontend-base-url:http://localhost:8088}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void requestReset(String email) {
        String normalized = email.trim().toLowerCase();
        userRepository.findByEmail(normalized).ifPresent(user -> {
            String token = generateToken();
            tokenRepository.save(PasswordResetToken.create(user, token, OffsetDateTime.now().plus(TOKEN_TTL)));
            String link = frontendBaseUrl + "/reset-password?token=" + token;
            emailService.send(
                    user.getEmail(),
                    "[셀러프로핏] 비밀번호 재설정",
                    "아래 링크를 눌러 비밀번호를 재설정하세요(30분 이내에만 유효합니다).\n\n" + link
                            + "\n\n본인이 요청하지 않았다면 이 메일은 무시하셔도 됩니다."
            );
        });
        // 위 ifPresent 가 비어도(가입 안 된 이메일) 여기서 예외 없이 조용히 끝난다 — 컨트롤러가 항상 204.
    }

    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 재설정 링크입니다."));
        if (resetToken.isUsed() || resetToken.isExpired()) {
            throw new IllegalArgumentException("만료되었거나 이미 사용된 재설정 링크입니다. 다시 요청해주세요.");
        }
        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        resetToken.markUsed();
        // user/resetToken 둘 다 영속 상태 — 트랜잭션 커밋 시 dirty checking 으로 저장(명시적 save 불필요).
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
