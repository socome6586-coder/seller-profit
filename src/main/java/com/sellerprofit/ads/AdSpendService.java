package com.sellerprofit.ads;

import com.sellerprofit.ads.domain.AdSpend;
import com.sellerprofit.ads.domain.AdSpendRepository;
import com.sellerprofit.ads.dto.AdSpendRequest;
import com.sellerprofit.ads.dto.AdSpendView;
import com.sellerprofit.ads.dto.ImportResult;
import com.sellerprofit.domain.MarketAccount;
import com.sellerprofit.repository.MarketAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 광고비 인제스트(수기 + CSV). 멱등 저장 + external_ref 생성 규칙(명세 §5)을 책임진다.
 *
 * <p>소유권 확인은 컨트롤러(AccountAccess.assertOwner)에서 이미 끝난 뒤 호출된다.
 * 여기서는 계정 참조를 로드해 저장한다. 매칭 실패(SKU 미존재)여도 저장하되 집계에서 '미할당'으로 다룬다.</p>
 */
@Service
public class AdSpendService {

    /** external_ref 의 빈 차원 토큰(고정). 빈 campaign/adGroup/keyword/vendorItemId 를 이 토큰으로 대체. */
    static final String EMPTY_TOKEN = "-";

    private final AdSpendRepository adSpendRepository;
    private final MarketAccountRepository marketAccountRepository;

    public AdSpendService(AdSpendRepository adSpendRepository,
                          MarketAccountRepository marketAccountRepository) {
        this.adSpendRepository = adSpendRepository;
        this.marketAccountRepository = marketAccountRepository;
    }

    /**
     * 멱등 external_ref 생성: {@code source:campaign:adGroup:keyword:vendorItemId:spendDate}.
     * 빈 차원은 고정 토큰("-")으로 대체하고, 각 토큰 내부의 ':' 는 '_' 로 치환해 구분자 충돌을 막는다.
     * 같은 행을 재업로드하면 같은 키가 나와 UNIQUE(market_account_id, external_ref) 로 중복이 차단된다.
     */
    public static String buildExternalRef(String source, String campaign, String adGroup,
                                          String keyword, String vendorItemId, LocalDate spendDate) {
        return String.join(":",
                norm(source),
                norm(campaign),
                norm(adGroup),
                norm(keyword),
                norm(vendorItemId),
                spendDate.toString()); // ISO yyyy-MM-dd
    }

    private static String norm(String token) {
        if (token == null) return EMPTY_TOKEN;
        String t = token.trim();
        if (t.isEmpty()) return EMPTY_TOKEN;
        return t.replace(':', '_');
    }

    /** 수기 1건 입력(멱등). 같은 키가 이미 있으면 기존 건을 돌려준다(중복 저장 안 함). */
    @Transactional
    public AdSpendView record(AdSpendRequest req) {
        String externalRef = buildExternalRef(
                AdSource.MANUAL, req.campaign(), req.adGroup(), req.keyword(),
                req.vendorItemId(), req.spendDate());

        return adSpendRepository
                .findByMarketAccountIdAndExternalRef(req.accountId(), externalRef)
                .map(AdSpendView::of)
                .orElseGet(() -> {
                    MarketAccount account = marketAccountRepository.getReferenceById(req.accountId());
                    AdSpend saved = adSpendRepository.save(AdSpend.create(
                            account, req.vendorItemId(), req.campaign(), req.adGroup(),
                            req.keyword(), req.spendDate(), req.amount(),
                            AdSource.MANUAL, externalRef));
                    return AdSpendView.of(saved);
                });
    }

    /**
     * CSV 업로드(멱등). 파싱 실패 행은 건너뛰고 리포트, 이미 존재하는 행은 신규로 세지 않는다.
     *
     * @return importedCount = 신규 저장 건수, skipped = 파싱 실패 행 리포트
     */
    @Transactional
    public ImportResult importCsv(Long accountId, String csvText) {
        List<AdCsvParser.RowResult> rows = AdCsvParser.parse(csvText);
        MarketAccount account = marketAccountRepository.getReferenceById(accountId);

        int imported = 0;
        List<ImportResult.SkippedRow> skipped = new ArrayList<>();
        java.util.Set<String> seenInThisFile = new java.util.HashSet<>();

        for (AdCsvParser.RowResult r : rows) {
            if (r.error() != null) {
                skipped.add(new ImportResult.SkippedRow(r.line(), r.error()));
                continue;
            }
            AdCsvParser.ParsedRow p = r.data();
            String externalRef = buildExternalRef(
                    AdSource.CSV, p.campaign(), p.adGroup(), p.keyword(),
                    p.vendorItemId(), p.spendDate());

            // 같은 파일 안 중복 행: 아직 flush 전이라 DB exists 로는 못 걸러짐 → 세트로 차단.
            if (!seenInThisFile.add(externalRef)) {
                continue; // 멱등: 같은 업로드 내 중복 → 신규 아님, 오류 아님
            }
            if (adSpendRepository.existsByMarketAccountIdAndExternalRef(accountId, externalRef)) {
                continue; // 멱등: 이미 있음 → 신규 아님, 오류 아님
            }
            adSpendRepository.save(AdSpend.create(
                    account, p.vendorItemId(), p.campaign(), p.adGroup(), p.keyword(),
                    p.spendDate(), p.amount(), AdSource.CSV, externalRef));
            imported++;
        }
        return new ImportResult(imported, skipped);
    }
}
