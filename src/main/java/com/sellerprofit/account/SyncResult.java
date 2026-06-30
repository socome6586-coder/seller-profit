package com.sellerprofit.account;

/**
 * 수동 동기화 결과. 소스별(주문/정산/반품)로 성공 건수 또는 실패 사유를 따로 담는다.
 *
 * <p>라이브 첫 호출에서 일부 소스만 실패할 수 있어(엔드포인트/필드 검증 중) 전체를 500 으로
 * 터뜨리지 않고 소스별 결과를 그대로 돌려준다 — 화면/로그에서 어디가 막혔는지 바로 보인다.
 */
public record SyncResult(SourceResult orders, SourceResult settlements, SourceResult returns) {

    public record SourceResult(boolean ok, Integer count, String error) {
        public static SourceResult ok(int count) {
            return new SourceResult(true, count, null);
        }

        public static SourceResult fail(String error) {
            return new SourceResult(false, null, error);
        }
    }
}
