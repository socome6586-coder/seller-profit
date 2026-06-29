package com.sellerprofit.repository;

import com.sellerprofit.domain.Cost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CostRepository extends JpaRepository<Cost, Long> {

    // 조회 기간과 겹치는 비용들 (앱에서 매출 비율로 배분)
    List<Cost> findByUserIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
            Long userId, LocalDate to, LocalDate from);
}
