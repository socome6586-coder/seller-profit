package com.sellerprofit.repository;

import com.sellerprofit.domain.AdminAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditRepository extends JpaRepository<AdminAudit, Long> {
    /** 관리자 화면(T10.5) 감사 로그 뷰용: 최신순. */
    Page<AdminAudit> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
