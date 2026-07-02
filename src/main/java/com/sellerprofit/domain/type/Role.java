package com.sellerprofit.domain.type;

/** 계정 권한. @Enumerated(STRING) 으로 저장 → DB엔 'USER','ADMIN' 그대로. 기본값 USER. */
public enum Role {
    USER,
    ADMIN
}
