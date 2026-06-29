package com.sellerprofit.account;

import com.sellerprofit.domain.MarketAccount;

/**
 * 로그인 유저의 마켓 계정 표현(대시보드 계정 선택용). 민감 키는 노출하지 않는다.
 */
public record AccountView(Long id, String channel, String vendorId) {

    public static AccountView of(MarketAccount account) {
        return new AccountView(
                account.getId(),
                account.getChannel().name(),
                account.getVendorId());
    }
}
