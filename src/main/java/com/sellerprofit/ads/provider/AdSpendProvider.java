package com.sellerprofit.ads.provider;

import com.sellerprofit.ads.domain.AdSpend;

import java.time.LocalDate;
import java.util.List;

/**
 * 외부 광고 플랫폼(쿠팡 광고 API 등)에서 광고비를 가져오는 어댑터의 인터페이스.
 *
 * <p><b>T6 — 자리만(백로그).</b> 쿠팡 광고 API 는 접근 권한·게이팅·응답 스키마가 아직 미확정이라
 * 지금은 구현체({@code CoupangAdsProvider} 등)를 만들지 않는다. v1 광고비 소스는 CSV 업로드/수기
 * 입력(T2, {@link com.sellerprofit.ads.AdSpendService})이며, 이 인터페이스는 그 소스를 나중에
 * "쿠팡 광고 API 자동 수집"으로 교체·병행할 때 상위 로직({@link com.sellerprofit.ads.AdRoiService} 등)이
 * 흔들리지 않도록 미리 경계를 그어두는 용도다.
 *
 * <p>docs/ad-roi-spec.md 의 {@code listSpends(accountId, from, to): AdSpend[]} 시그니처를 그대로
 * 따르되, 이 코드베이스의 관례(리포지토리/서비스 전반이 배열 대신 {@link List} 사용)에 맞춰
 * 반환 타입만 {@code List<AdSpend>} 로 옮겼다.
 */
public interface AdSpendProvider {

    /**
     * 지정한 기간(from~to, 양끝 포함)에 발생한 광고비를 외부 플랫폼에서 조회한다.
     *
     * <p>구현체는 결과를 저장(persist)하지 않는다 — 조회만 담당하고, 멱등 저장(external_ref 기반)은
     * 호출하는 서비스 계층의 책임이다(T2 의 CSV/수기 임포트와 동일한 저장 경로를 공유하게 될 것).
     *
     * @param accountId 쿠팡 마켓 계정 ID ({@code market_accounts.id})
     * @param from      조회 시작일(포함)
     * @param to        조회 종료일(포함)
     * @return 해당 기간의 광고비 목록(아직 영속화 전인 경우도 있을 수 있음 — 구현체 계약에 따름)
     */
    List<AdSpend> listSpends(Long accountId, LocalDate from, LocalDate to);
}
