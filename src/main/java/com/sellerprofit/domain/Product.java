package com.sellerprofit.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
    name = "products",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_product_vendor_item",
        columnNames = {"market_account_id", "vendor_item_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_account_id", nullable = false)
    private MarketAccount marketAccount;

    @Column(name = "vendor_item_id", nullable = false, length = 50)
    private String vendorItemId;          // 쿠팡 옵션상품 식별자

    @Column(nullable = false, length = 500)
    private String name;

    // 금액은 항상 BigDecimal. precision/scale 을 컬럼과 일치시킨다.
    @Column(precision = 14, scale = 2)
    private BigDecimal cogs;               // 매입원가, null = 미입력

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** 수집 시 신규 상품 생성용. protected 기본 생성자는 JPA 전용으로 유지한다. */
    public static Product create(MarketAccount marketAccount, String vendorItemId, String name) {
        Product p = new Product();
        p.marketAccount = marketAccount;
        p.vendorItemId = vendorItemId;
        p.name = name;
        return p;
    }
}
