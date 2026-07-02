package com.sellerprofit.admin;

import com.sellerprofit.admin.dto.AdminUserView;
import com.sellerprofit.domain.User;
import com.sellerprofit.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자용 유저 조회(T10.2). CMS 를 만들지 않는다 — 목록/검색/페이지네이션만 최소로 제공한다.
 */
@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 유저 목록. email 이 비어있지 않으면 부분검색(대소문자 무시), 최신 가입순(id desc).
     *
     * @param email 부분검색어(선택, null/blank 면 전체)
     * @param page  0-base 페이지 번호(음수는 0으로 보정)
     * @param size  페이지 크기(1~100 로 보정, 기본 20)
     */
    @Transactional(readOnly = true)
    public Page<AdminUserView> list(String email, Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));

        Page<User> users = (email == null || email.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCase(email.trim(), pageable);

        return users.map(AdminUserView::of);
    }

    /** 단건 조회. 지급/role 변경 후 최신 상태를 응답으로 돌려줄 때 쓴다. */
    @Transactional(readOnly = true)
    public AdminUserView get(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User 없음: " + userId));
        return AdminUserView.of(user);
    }
}
