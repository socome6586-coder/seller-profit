package com.sellerprofit.account;

import com.sellerprofit.repository.MarketAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 마켓 계정 소유권 가드. 로그인 유저가 자기 계정에만 접근하도록 강제한다.
 *
 * <p>현재는 컨트롤러가 accountId 를 직접 받지만, 그 계정이 세션 유저 소유인지 여기서 확인한다.
 * 소유가 아니거나 없는 계정이면 동일하게 "없음" 으로 처리해 **계정 존재 여부 노출을 막는다**
 * (다른 셀러 계정 id 를 추측해도 구분 불가).
 */
@Component
public class AccountAccess {

    private final MarketAccountRepository marketAccountRepository;

    public AccountAccess(MarketAccountRepository marketAccountRepository) {
        this.marketAccountRepository = marketAccountRepository;
    }

    /** accountId 가 userId 소유가 아니면 예외(→ 400, "없음"). */
    public void assertOwner(Long accountId, Long userId) {
        if (accountId == null || !marketAccountRepository.existsByIdAndUserId(accountId, userId)) {
            throw new IllegalArgumentException("MarketAccount 없음: " + accountId);
        }
    }

    /** 로그인 유저의 마켓 계정 목록(대시보드 계정 선택용). */
    @Transactional(readOnly = true)
    public List<AccountView> myAccounts(Long userId) {
        return marketAccountRepository.findByUserId(userId).stream()
                .map(AccountView::of)
                .toList();
    }
}
