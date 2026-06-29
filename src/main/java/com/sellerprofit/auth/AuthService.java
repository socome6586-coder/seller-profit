package com.sellerprofit.auth;

import com.sellerprofit.auth.dto.AuthUserView;
import com.sellerprofit.domain.User;
import com.sellerprofit.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 처리. 비밀번호는 BCrypt 해시로만 저장한다(평문 금지).
 *
 * 가입자는 별도 결제 없이 무료(FREE) 플랜으로 시작한다 — 유저 확보 우선.
 * 로그인/세션은 Phase 2, 유료 전환(토스 빌링)은 Phase 3.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthUserView signup(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("이미 가입된 이메일입니다.");
        }
        User user = userRepository.save(
                User.create(normalizedEmail, passwordEncoder.encode(rawPassword)));
        // 기본값이 FREE 라 추가 결제 없이 바로 사용 가능.
        return AuthUserView.of(user);
    }
}
