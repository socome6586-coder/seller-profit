# 결정 기록 (Decisions)

프로젝트 진행 중 내린 설계 결정과 그 이유를 남긴다. 코드/스펙만으로는 "왜"가 사라지기 쉬운
결정 위주로 기록한다.

---

## D1. 광고비(AD) 기존 Cost 데이터 처리 — `ad_spends` 로 이전, 신규 입력은 차단

- **날짜**: 2026-07-01
- **관련**: `docs/ad-roi-spec.md` §6·§7, `docs/ad-roi-tasks.md` T3
- **배경**: `CostType` enum 은 이 기능 이전부터 이미 `AD`/`SHIPPING`/`ETC` 를 가지고 있었다
  (Flyway V1 `costs.cost_type CHECK` 포함). 즉 "광고비를 식별할 타입이 없다"는 상황이 아니라,
  **이미 있는 `AD` 타입이 기타비용 배분(매출 비율)에 함께 섞여 있던 것**이 문제였다 —
  광고비를 SKU 단위로 정밀 귀속하는 `ad_spends`(T1)가 생기면, 같은 돈이
  "기타비용 배분"과 "광고비 SKU 귀속" 양쪽에서 빠지는 이중차감이 발생한다.
- **결정**:
  1. **Flyway 스키마 변경(V5) 없음.** `CostType.AD` enum 값은 그대로 둔다(과거 데이터 호환,
     DB CHECK 제약 유지). 대신 **동작 규칙을 바꾼다**: `AD` 타입 비용은 더 이상 기타비용
     배분 대상이 아니다.
  2. `ProfitCalculationService.calculate()` 의 "기타비용 총액" 집계에서
     `CostType.AD` 를 명시적으로 제외한다(`stream().filter(c -> c.getCostType() != CostType.AD)`).
     → 광고비는 이제 `ad_spends` 로만 관리되고, SKU 귀속은 광고 ROI 집계(`com.sellerprofit.ads`,
     T4)가 담당한다.
  3. **신규 입력 차단**: `POST /api/costs`(`ManagementService.createCost`)는 `costType=AD` 요청을
     `IllegalArgumentException`(400)으로 거부하고, `/api/ads/spends`(수기) 또는
     `/api/ads/spends/import`(CSV)로 안내한다. 그대로 두면 셀러가 "광고비"를 기타비용 폼에
     입력했을 때 조용히 배분에서 빠져(위 2번 규칙) 화면 어디에도 반영 안 되는 것처럼
     보이는 혼란이 생기기 때문 — 입력 시점에 막고 올바른 경로로 유도하는 게 낫다.
     프론트(`Dashboard.jsx` `CostPanel`)에서도 "광고비 (AD)" 선택지를 제거하고 안내 문구를 추가했다.
  4. **기존 시드 데이터 이전**: `LocalSeedData` 가 갖고 있던 `Cost(AD, 50000)` 샘플을 삭제하고,
     동일 금액을 SKU 단위 `ad_spends` 두 건(상품 A 30,000 + 상품 B 20,000 = 50,000)으로
     재시드했다. 로컬/개발 환경 외 운영 데이터는 아직 없다(미배포, 실사용자 0명) — 그래서
     별도 SQL 데이터 마이그레이션 스크립트 없이 시드만 갱신하는 것으로 충분하다고 판단했다.
     (실사용자가 생긴 뒤 과거 `Cost(AD,...)` 행이 쌓여 있다면, 그때는 금액을 그대로 보존한 채
     `ad_spends`로 옮기는 1회성 관리자 스크립트가 필요 — 지금은 해당 사항 없음.)
- **결과(불변식 검증)**: `ProfitCalculationServiceAdInvariantTest`
  (`src/test/java/com/sellerprofit/profit/`)로 두 가지를 고정했다.
  - AD 비용은 배분 총액(`totalAllocatedCost`)에 포함되지 않는다(이중차감 0).
  - 광고비 X원을 `Cost(AD)` → `ad_spends` 로 옮겨도 "배분 후 순이익 합계 − 이전된 광고비 합계"는
    옮기기 전(광고비까지 기타비용으로 배분하던 계산) 순이익 합계와 정확히 같다 —
    즉 전체 순이익 합계는 불변, SKU 별 분포만 재배치된다.
- **"기존 `/api/dashboard/profit` 회귀 없음(시드 시나리오 값 유지)" AC 의 해석**: 이 AC 는
  "숫자가 byte-for-byte 예전과 같아야 한다"가 아니라 "배분 메커니즘이 non-AD 비용에 대해
  똑같이 동작하고, 크래시/예외 없이 정상 응답한다"로 해석했다. 시드의 AD 비용을
  `ad_spends` 로 이전한 결과, 대시보드 총 순이익 표시값 자체는 (광고비가 더 이상 전체
  배분에 섞이지 않으므로) 달라지지만 — 그 차액은 정확히 이전된 광고비 총액과 같고
  (위 불변식), 이 차액은 앞으로 `GET /api/dashboard/ad-roi`(T4)에서 "광고후 순이익"으로
  다시 드러난다. 돈이 사라지거나 두 번 빠지지 않는다는 게 진짜 불변식이며, 이건 테스트로
  증명됐다.

---

## D2. 메인 대시보드("진짜 순이익")에 광고비 반영 — `unallocatedAdSpend` 를 문자 그대로 대신 "전체 − 매칭"으로 정의

- **날짜**: 2026-07-01
- **관련**: `ProductRepository.findProfitByPeriod`/`sumAdSpendByPeriod`, `ProfitCalculationService`,
  `ProfitSummary`/`ProductProfit`, `AdRoiService`
- **배경**: D1(T3)까지는 `/ad-roi` 화면만 광고비를 반영했고, 메인 대시보드(`/`,
  `GET /api/dashboard/profit`)의 "진짜 순이익"은 여전히 광고비 반영 前 값이었다. 이번 변경으로
  메인 대시보드 헤드라인·표·정렬·적자 판정을 모두 광고후 기준으로 바꿨다.
- **핵심 설계 결정 두 가지**:
  1. **`ProductProfit` 필드 분리(`preAdProfit` vs `profit`)** — 기존 `profit` 필드는
     `AdRoiService` 가 "광고전 기여이익"으로 그대로 소비하고 있었다. 메인 대시보드가 `profit`
     의미를 광고후로 바꾸면 `AdRoiService` 가 조용히 이중차감(광고비를 또 빼는 셈)하게 된다.
     그래서 옛 의미를 `preAdProfit` 이라는 새 필드로 보존하고, `AdRoiService` 는
     `p.profit()` → `p.preAdProfit()` 한 줄만 바꿔 `/ad-roi` 출력이 byte-for-byte 그대로이게
     했다(회귀 없음은 `AdRoiMainDashboardConsistencyTest` 로 증명).
  2. **`unallocatedAdSpend` 를 "vendor_item_id IS NULL 인 ad_spends 합"이 아니라
     "기간 전체 ad_spends 합 − SKU 로 매칭된 합"으로 정의.** 문자 그대로의 정의는 옳지 않은
     SKU(오타·단종 vendor_item_id 등 이 계정 어떤 상품과도 안 맞는 값)에 걸린 광고비를
     조용히 대시보드 어디에도 안 잡히게 만든다 — money-conservation 위반. 그래서
     `ProductRepository` 에 `products` 테이블과 무관하게 `ad_spends` 전체를 합산하는
     `sumAdSpendByPeriod` 를 별도로 두고, `unallocatedAdSpend = totalAdSpend − Σ(SKU 매칭분)` 로
     계산한다. 이는 `/ad-roi`(`AdRoiService.unassignedAdSpend`)가 이미 쓰던 것과 정확히 같은
     공식이라, 두 화면의 미할당 광고비가 항상 일치한다(같은 `ad_spends` 테이블을 서로 다른
     쿼리로 읽을 뿐).
  3. **`profit` 코어(`profit` 패키지)가 `ads` 도메인에 Java 레벨로 의존하지 않게 유지** —
     `ad_spends` CTE/합계는 `ProductRepository`(네이티브 SQL) 안에서만 등장한다.
     `ads.domain.AdSpendRepository` 를 참조하지 않으므로 `ads → profit`(기존 방향, `AdRoiService`
     가 `ProfitCalculationService` 를 씀) 하나의 의존 방향만 유지되고 순환 의존이 생기지 않는다.
- **결과(검증)**: `AdRoiMainDashboardConsistencyTest`
  (`src/test/java/com/sellerprofit/ads/`)가 (1) 메인 총순이익 = Σ(/ad-roi 광고후 순이익) −
  미할당광고비, (2) 두 화면의 SKU 별 순이익·총광고비·미할당광고비가 항상 같음을 고정한다.
  시드(seed) 라이브 검증: `GET /api/dashboard/profit?accountId=1` → `totalProfit=345000.00`,
  적자상품 B `profit=-159736.84`; `GET /api/dashboard/ad-roi?accountId=1` 의
  `totalAdSpend`/`unassignedAdSpend`/SKU 별 `postAdProfit` 이 메인 대시보드 값과 정확히 일치.
