package com.sellerprofit.repository;

import com.sellerprofit.domain.ReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReturnItemRepository extends JpaRepository<ReturnItem, Long> {

    boolean existsByMarketAccountIdAndExternalRef(Long marketAccountId, String externalRef);
}
