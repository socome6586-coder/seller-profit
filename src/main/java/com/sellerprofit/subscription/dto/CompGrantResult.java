package com.sellerprofit.subscription.dto;

import java.time.OffsetDateTime;

/** 무상(COMP) 지급 결과. 감사 로그의 before/after 기록용. */
public record CompGrantResult(OffsetDateTime before, OffsetDateTime after) {
}
