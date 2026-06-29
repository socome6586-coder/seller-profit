package com.sellerprofit.repository;

import com.sellerprofit.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    boolean existsByMarketAccountIdAndExternalRef(Long marketAccountId, String externalRef);
}
