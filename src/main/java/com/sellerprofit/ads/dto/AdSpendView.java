package com.sellerprofit.ads.dto;

import com.sellerprofit.ads.domain.AdSpend;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 광고비 1건 응답 뷰(수기 입력 결과 등). 내부 식별자(external_ref)는 노출하지 않는다.
 */
public record AdSpendView(
        Long id,
        String vendorItemId,
        String campaign,
        String adGroup,
        String keyword,
        LocalDate spendDate,
        BigDecimal amount,
        String source
) {
    public static AdSpendView of(AdSpend s) {
        return new AdSpendView(
                s.getId(), s.getVendorItemId(), s.getCampaign(), s.getAdGroup(),
                s.getKeyword(), s.getSpendDate(), s.getAmount(), s.getSource());
    }
}
