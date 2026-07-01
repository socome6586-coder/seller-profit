package com.sellerprofit.ads.domain;

import com.sellerprofit.domain.MarketAccount;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 광고비 1건(spend). SKU(vendor_item_id) 단위로 귀속해 광고 ROI 를 집계한다.
 *
 * <p>바운디드 컨텍스트 {@code com.sellerprofit.ads} 에 격리한다(profit 코어 최소 침습).
 * 스키마는 Flyway V4({@code ad_spends}) 와 일치. 금액은 항상 {@link BigDecimal}(NUMERIC(14,2)).</p>
 *
 * <p>멱등성: {@code (market_account_id, external_ref)} UNIQUE. CSV 재업로드 시 같은 행이
 * 중복 저장되지 않도록 {@code external_ref} 로 차단한다(생성 규칙은 T2 서비스가 담당).</p>
 */
@Entity
@Table(
    name = "ad_spends",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_ad_spend_external_ref",
        columnNames = {"market_account_id", "external_ref"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdSpend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_account_id", nullable = false)
    private MarketAccount marketAccount;

    /** SKU. null = 미할당(캠페인 단위만 있는 spend). products 매칭 실패해도 저장하되 '미할당'으로 집계. */
    @Column(name = "vendor_item_id", length = 50)
    private String vendorItemId;

    @Column(length = 255)
    private String campaign;         // 차원(rollup 용)

    @Column(name = "ad_group", length = 255)
    private String adGroup;          // 차원

    @Column(length = 255)
    private String keyword;          // 차원

    @Column(name = "spend_date", nullable = false)
    private LocalDate spendDate;     // 광고 집행일(집계 기준)

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;       // amount >= 0 (스키마 CHECK)

    @Column(nullable = false, length = 20)
    private String source;           // 'MANUAL' | 'CSV' | 'COUPANG_ADS'(후속)

    @Column(name = "external_ref", nullable = false, length = 300)
    private String externalRef;      // 멱등 키

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /**
     * 외부 패키지(ads 서비스/시드)에서 광고비 1건을 생성한다. protected 기본 생성자는 JPA 전용.
     *
     * @param vendorItemId SKU. null 허용(미할당)
     * @param campaign/adGroup/keyword 차원. null 허용
     * @param externalRef 멱등 키(생성 규칙은 AdSpendService 가 담당)
     */
    public static AdSpend create(
            MarketAccount marketAccount,
            String vendorItemId,
            String campaign,
            String adGroup,
            String keyword,
            LocalDate spendDate,
            BigDecimal amount,
            String source,
            String externalRef) {
        AdSpend s = new AdSpend();
        s.marketAccount = marketAccount;
        s.vendorItemId = vendorItemId;
        s.campaign = campaign;
        s.adGroup = adGroup;
        s.keyword = keyword;
        s.spendDate = spendDate;
        s.amount = amount;
        s.source = source;
        s.externalRef = externalRef;
        return s;
    }
}
