package com.sellerprofit.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 로그인 없이도 조회 가능한 공개 설정값. 인증/소유권 검증이 필요 없는 정적 값만 다룬다.
 *
 * 현재는 하나뿐 — 계정 연동 가이드(프론트 Accounts.jsx)가 쿠팡 OPEN API 키 발급 시
 * "IP 주소" 등록 안내에 쓰는 운영 서버 공인 IP. 이 값을 프론트 코드에 문자열로 흩뿌리지
 * 않기 위해 {@code app.public-server-ip}(application.yml, 배포 시 APP_PUBLIC_SERVER_IP
 * 환경변수로 오버라이드 가능) 한 곳에서만 관리하고, 여기서 API 로 내려준다 — 서버 이전 시
 * 이 설정값 하나만 바꾸면 가이드 화면이 자동으로 갱신된다(docs/onboarding-tasks.md §2).
 */
@RestController
@RequestMapping("/api/config")
public class PublicConfigController {

    private final String publicServerIp;

    public PublicConfigController(@Value("${app.public-server-ip}") String publicServerIp) {
        this.publicServerIp = publicServerIp;
    }

    @GetMapping
    public Map<String, String> get() {
        return Map.of("publicServerIp", publicServerIp);
    }
}
