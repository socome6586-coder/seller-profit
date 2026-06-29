package com.sellerprofit.domain;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 20)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.FREE;

    @Column(name = "current_period_end")
    private OffsetDateTime currentPeriodEnd;

    // created_at / updated_at 는 DB 기본값 + 트리거가 소유 → JPA는 읽기만
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** 회원 생성용. passwordHash 는 호출측에서 BCrypt 등으로 해싱해 전달한다. */
    public static User create(String email, String passwordHash) {
        User u = new User();
        u.email = email;
        u.passwordHash = passwordHash;
        return u;
    }
}
