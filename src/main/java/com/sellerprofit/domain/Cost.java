package com.sellerprofit.domain;

import com.sellerprofit.domain.type.CostType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "costs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_type", nullable = false, length = 20)
    private CostType costType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(length = 255)
    private String memo;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** 기타비용 입력용. 기간 총액을 앱이 매출 비율로 배분한다. */
    public static Cost create(User user, CostType costType, BigDecimal amount,
                              LocalDate periodStart, LocalDate periodEnd, String memo) {
        Cost c = new Cost();
        c.user = user;
        c.costType = costType;
        c.amount = amount;
        c.periodStart = periodStart;
        c.periodEnd = periodEnd;
        c.memo = memo;
        return c;
    }
}
