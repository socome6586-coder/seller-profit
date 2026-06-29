package com.sellerprofit.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 반품/취소 1건 (쿠팡 '반품요청 목록'의 상품 라인 단위).
 *
 * 매출은 정산(settlements)이 음수로 반영하지만 수량은 반영하지 못하므로,
 * 이 수량을 'COGS 기준 수량 = 주문수량 − 반품수량' 보정에 사용한다.
 */
@Entity
@Table(
    name = "return_items",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_return_item_ref",
        columnNames = {"market_account_id", "external_ref"}
    )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnItem {

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

    @Column(name = "coupang_order_id", length = 50)
    private String coupangOrderId;          // 원주문 번호(있으면)

    @Column(name = "vendor_item_id", nullable = false, length = 50)
    private String vendorItemId;

    @Column(name = "external_ref", nullable = false, length = 100)
    private String externalRef;             // 쿠팡 반품 식별자 (멱등 키)

    @Column(nullable = false)
    private int quantity;                   // 반품 수량(양수)

    @Column(length = 255)
    private String reason;                  // 반품 사유(참고)

    @Column(nullable = false, length = 30)
    private String status;                  // 쿠팡 반품 상태값 원본

    @Column(name = "requested_at", nullable = false)
    private LocalDate requestedAt;          // 반품 접수일(집계 기준)

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** 반품 수집 시 생성용. product 는 매칭되는 상품이 없으면 null 허용. */
    public static ReturnItem create(MarketAccount marketAccount, Product product,
                                    String coupangOrderId, String vendorItemId,
                                    String externalRef, int quantity,
                                    String reason, String status, LocalDate requestedAt) {
        ReturnItem r = new ReturnItem();
        r.marketAccount = marketAccount;
        r.product = product;
        r.coupangOrderId = coupangOrderId;
        r.vendorItemId = vendorItemId;
        r.externalRef = externalRef;
        r.quantity = quantity;
        r.reason = reason;
        r.status = status;
        r.requestedAt = requestedAt;
        return r;
    }
}
