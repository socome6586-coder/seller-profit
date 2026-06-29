package com.sellerprofit.repository;

import com.sellerprofit.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // 수집 멱등성: 이미 적재된 주문 라인인지 사전 체크 (혹은 UNIQUE 제약 위반을 catch)
    boolean existsByMarketAccountIdAndCoupangOrderIdAndVendorItemId(
            Long marketAccountId, String coupangOrderId, String vendorItemId);
}
