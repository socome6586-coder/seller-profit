package com.sellerprofit.ads;

/**
 * 광고비 데이터 소스(ad_spends.source). 스키마 CHECK 와 문자열이 일치해야 한다.
 * COUPANG_ADS 는 후속(Provider) 자리만 잡아둔다(T6).
 */
public final class AdSource {
    public static final String MANUAL = "MANUAL";
    public static final String CSV = "CSV";
    public static final String COUPANG_ADS = "COUPANG_ADS";

    private AdSource() {}
}
