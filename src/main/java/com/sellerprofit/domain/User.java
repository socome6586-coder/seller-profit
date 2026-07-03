package com.sellerprofit.domain;

import com.sellerprofit.crypto.EncryptedStringConverter;
import com.sellerprofit.domain.type.Role;
import com.sellerprofit.domain.type.SubscriptionSource;
import com.sellerprofit.domain.type.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;            // BCrypt 해시 (평문 저장 금지)

    // 휴대전화번호(숫자만 정규화해 저장, 예: "01012345678"). 중복가입 방지용 UNIQUE.
    // 마이그레이션 이전 가입자를 위해 컬럼은 NULL 허용이지만, 신규 가입은 서비스 레벨에서 필수로 강제한다.
    @Column(name = "phone", unique = true, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.FREE;

    @Column(name = "current_period_end")
    private OffsetDateTime currentPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    // 구독 취득 경로. PAID=결제, COMP=관리자 무상 지급. 매출 집계는 반드시 PAID 만 포함해야 한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SubscriptionSource source = SubscriptionSource.PAID;

    // 토스 빌링키 — 카드에 준하는 민감정보라 엔티티엔 평문, DB엔 암호화 BYTEA. 로그 노출 금지.
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "billing_key_encrypted")
    private String billingKey;

    // 토스 customerKey(비식별). 빌링키 발급/결제 호출에 함께 보낸다. PII 금지.
    @Column(name = "billing_customer_key", length = 64)
    private String billingCustomerKey;

    @Column(name = "last_billed_at")
    private OffsetDateTime lastBilledAt;

    // created_at / updated_at 는 DB 기본값 + 트리거가 소유 → JPA는 읽기만
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** 회원 생성용. passwordHash 는 호출측에서 BCrypt 등으로 해싱해 전달한다. */
    public static User create(String email, String passwordHash, String phone) {
        User u = new User();
        u.email = email;
        u.passwordHash = passwordHash;
        u.phone = phone;
        return u;
    }

    /** phone 없이 생성(테스트/내부용 헬퍼 등 실제 가입 흐름이 아닌 경우). */
    public static User create(String email, String passwordHash) {
        return create(email, passwordHash, null);
    }
}
