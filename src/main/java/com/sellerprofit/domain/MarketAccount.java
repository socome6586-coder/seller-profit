package com.sellerprofit.domain;

import com.sellerprofit.crypto.EncryptedStringConverter;
import com.sellerprofit.domain.type.Channel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "market_accounts",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_market_account",
        columnNames = {"user_id", "channel", "vendor_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Channel channel = Channel.COUPANG;

    @Column(name = "vendor_id", nullable = false, length = 50)
    private String vendorId;             // 쿠팡 업체코드

    // 엔티티에선 평문 String, DB엔 암호화된 BYTEA. 로그에 절대 노출 금지.
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_key_encrypted", nullable = false)
    private String accessKey;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "secret_key_encrypted", nullable = false)
    private String secretKey;

    // 증분 동기화 커서 — 다음 수집 시작 지점
    @Column(name = "last_order_synced_at")
    private OffsetDateTime lastOrderSyncedAt;

    @Column(name = "last_settlement_synced_at")
    private OffsetDateTime lastSettlementSyncedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    // toString 자동 생성 시 키가 찍히지 않도록 의도적으로 Lombok @ToString 미사용
}
