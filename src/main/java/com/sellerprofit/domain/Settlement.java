package com.sellerprofit.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "settlements",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_settlement_ref",
        columnNames = {"market_account_id", "external_ref"}
    )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_account_id", nullable = false)
    private MarketAccount marketAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "vendor_item_id", nullable = false, length = 50)
    private String vendorItemId;

    @Column(name = "external_ref", nullable = false, length = 100)
    private String externalRef;            // 쿠팡 정산 식별자 (멱등 키)

    @Column(name = "payout_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal payoutAmount;        // 실지급액 (반품 시 음수)

    @Column(name = "fee_amount", precision = 14, scale = 2)
    private BigDecimal feeAmount;           // 참고용 수수료

    @Column(name = "settled_at", nullable = false)
    private LocalDate settledAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
