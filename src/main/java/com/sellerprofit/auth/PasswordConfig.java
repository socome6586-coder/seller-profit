package com.sellerprofit.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해싱 빈. 전체 스프링 시큐리티 필터체인 없이 BCrypt 인코더만 제공한다.
 * (엔드포인트 보안은 Phase 2 에서 세션/인증으로 추가)
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
