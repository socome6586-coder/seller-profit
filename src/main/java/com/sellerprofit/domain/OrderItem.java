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
    name = "order_items",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_order_item",
        columnNames = {"market_account_id", "coupang_order_id", "vendor_item_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_account_id", nullable = false)
    private MarketAccount marketAccount;

    // ON DELETE SET NULL 대응 → nullable
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "coupang_order_id", nullable = false, length = 50)
    private String coupangOrderId;

    @Column(name = "vendor_item_id", nullable = false, length = 50)
    private String vendorItemId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "sale_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal salePrice;

    @Column(nullable = false, length = 30)
    private String status;                 // 쿠팡 상태값 원본 보관

    @Column(name = "ordered_at", nullable = false)
    private OffsetDateTime orderedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 수집 시 주문 라인 생성용. */
    public static OrderItem create(MarketAccount marketAccount, Product product,
                                   String coupangOrderId, String vendorItemId,
                                   int quantity, BigDecimal salePrice,
                                   String status, OffsetDateTime orderedAt) {
        OrderItem o = new OrderItem();
        o.marketAccount = marketAccount;
        o.product = product;
        o.coupangOrderId = coupangOrderId;
        o.vendorItemId = vendorItemId;
        o.quantity = quantity;
        o.salePrice = salePrice;
        o.status = status;
        o.orderedAt = orderedAt;
        return o;
    }
}
