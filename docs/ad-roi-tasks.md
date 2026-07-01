# 작업지시서 — 광고 ROI 기능 (Claude Code 용)

> 저장소 위치 권장: `docs/ad-roi-tasks.md`
> **작업 전 필독:** 루트 `CLAUDE.md`(프로젝트 규약)와 `docs/ad-roi-spec.md`(기능 명세) 를 먼저 읽는다.
> 이 저장소의 기존 컨벤션을 **그대로 계승**한다: 정산=매출원천, 멱등 `external_ref`, NUMERIC(14,2),
> 소유권 가드(`CurrentUser`/`AccountAccess`), `ApiExceptionHandler` 400/401 JSON,
> Hibernate `validate` → **스키마 변경은 반드시 Flyway 마이그레이션(다음 번호 V4)으로만**.

## 공통 규칙 (모든 태스크)

- 새 코드는 신규 패키지 **`com.sellerprofit.ads`** 에 격리(바운디드 컨텍스트). `profit` 코어는 최소 침습.
- 외부/파싱/정규화 로직은 **단위 테스트 필수**(픽스처 기반, 기존 `ReturnExternalRefTest` 스타일).
- 커밋: Conventional Commits, 태스크 단위로 작게. 시크릿·키는 절대 커밋/로그 금지.
- 각 태스크는 **AC(완료조건)** 를 만족해야 완료. 완료 시 AC 체크리스트를 결과로 보고.
- 프론트 수정 시 `cd frontend && npm run build` 후 산출물(`static/`) 커밋(기존 규칙).

---

## T1 — 스키마 & 도메인 (`ad_spends`)

- Flyway **`V4__ad_spends.sql`**: `docs/ad-roi-spec.md` §5 스키마. 멱등 `UNIQUE(market_account_id, external_ref)`,
  `CHECK(amount >= 0)`, 인덱스 2개, `updated_at` 트리거(기존 V1/V2 트리거 방식 재사용).
- `ads/domain/AdSpend` 엔티티(+ 정적 팩토리 `AdSpend.create(...)`, 외부 패키지 생성용), `AdSpendRepository`.
- **AC:** 앱 기동 시 V4 적용 + Hibernate `validate` 통과. 엔티티 ↔ 테이블 매핑 일치. 팩토리로 저장/조회되는 단위 테스트 통과.

## T2 — 광고비 인제스트 (수기 + CSV)

- `ads/` 패키지: `AdSpendService`(멱등 저장, `external_ref` 생성=명세 §5 규칙), `AdsController`.
  - `POST /api/ads/spends` — 수기 1건(record DTO, jakarta 검증: `@NotNull/@PositiveOrZero/@NotBlank`).
  - `POST /api/ads/spends/import` — CSV 업로드. 파서는 **컬럼 매핑을 상수/설정으로 분리**(실제 쿠팡 광고 CSV 로 확정 전까지 `[검증 포인트]` 주석). 파싱 실패 행은 **건너뛰고 리포트**(`{importedCount, skipped:[{row, reason}]}`).
- 소유권: 세션 주체 → `AccountAccess.assertOwner(accountId, userId)`. 실패는 열거 차단("없음"). 검증/오류는 `ApiExceptionHandler` 400 JSON.
- **AC:**
  - [ ] 같은 CSV 2회 업로드 → 2번째는 신규 0건(멱등, `external_ref` UNIQUE).
  - [ ] 잘못된 행이 섞인 CSV → 정상 행만 저장 + skipped 리포트 반환.
  - [ ] 타 유저 accountId 로 입력 시 400 "없음"(열거 차단).
  - [ ] 단위 테스트: `external_ref` 생성 규칙(빈 차원 토큰·SKU 유무).

## T3 — 이중차감 제거 + 순이익 엔진 정합 (가장 중요)

- `docs/ad-roi-spec.md` §6·§7 규칙 구현.
  - 광고성 비용을 `ProfitCalculationService`/`findProfitByPeriod` 의 **기타비용 배분에서 제외**.
  - 기존 광고비 `Cost` 처리 방침 결정·구현(비용 타입에 `AD` 추가 or "기타비용에 광고 미포함" 규칙). 결정은 `docs/DECISIONS.md` 에 기록.
  - 필요 시 Flyway **`V5`**(비용 타입 enum 확장) 추가.
- **AC (불변식 — 반드시 테스트로 증명):**
  - [ ] 광고비 X원을 Cost → `ad_spends` 로 이전하면 **대시보드 전체 순이익 합계 불변**, SKU별 분포만 변함.
  - [ ] 같은 광고비가 순이익에서 **두 번 빠지지 않음**(이중차감 0). 단위/통합 테스트로 검증.
  - [ ] 기존 `/api/dashboard/profit` 회귀 없음(시드 시나리오 값 유지).

## T4 — 광고 ROI 집계 쿼리 + API

- `ads/` 에 SKU별 집계: 광고전 기여이익 / 광고비 / 광고후 순이익 / 광고손실 플래그 + 헤드라인(재검토 대상 광고비, 미할당 광고비). 명세 §8·§9.
  - **CTE 로 JOIN fan-out 방지**(기존 `findProfitByPeriod` 규율). 광고비 CTE 를 기존 집계에 얹는 형태.
  - `GET /api/dashboard/ad-roi?accountId=&from=&to=`(기간 생략 시 최근 30일). DTO `AdRoiSummary`(헤드라인+미할당) + `AdRoiRow`(SKU 행). 소유권 강제.
- **AC:**
  - [ ] 광고손실 SKU(광고비>광고전기여이익)가 플래그 true 로, 표 최상단 정렬.
  - [ ] 미할당 광고비가 별도 버킷으로 정확히 합산.
  - [ ] 헤드라인 "재검토 대상 광고비" = 광고손실 SKU 광고비 합과 일치.
  - [ ] 시드 확장 시나리오로 손계산과 응답 값 일치(단위 테스트/픽스처).

## T5 — 프론트 (광고 효율 화면)

- 새 화면 `AdRoi.jsx`(`/ad-roi`): 헤드라인 카드(재검토 대상/미할당) + SKU 표(광고손실 빨강·상단, 기존 `적자` UI 관용 재사용) + **CSV 업로드 UI**(결과: imported/skipped) + 미할당 경고 배너.
- accountId 는 기존 `/api/me/accounts` 드롭다운 패턴 재사용. `App.jsx` 라우트 + Nav 항목 + **`SpaForwardingController` 에 `/ad-roi` 포워드 추가**(딥링크 유지).
- (선택) **플랜 게이팅**: FREE 요약/제한, PRO 전체. `PlanType` 기존 게이팅 패턴 재사용(가격/한도는 임시값).
- **AC:**
  - [ ] :8088 에서 `/ad-roi` 딥링크 forward 200, 화면 렌더(콘솔 에러 0).
  - [ ] CSV 업로드 → 결과 표시 → 표/헤드라인 자동 갱신.
  - [ ] 광고손실 SKU 빨강·상단. 모바일 폭 레이아웃 안 깨짐.
  - [ ] `npm run build` 산출물 커밋됨.

## T6 — (백로그, 지금 만들지 말 것) 쿠팡 광고 API Adapter

- 인터페이스 **자리만**: `ads/provider/AdSpendProvider`(`listSpends(accountId, from, to): AdSpend[]`). 구현(`CoupangAdsProvider`)은 후속.
- 사유: 쿠팡 광고 API 접근·게이팅·스키마 미확정. 확정 전까지 CSV/수기(T2)가 소스. 인터페이스만 두어 후속 교체를 흡수.

---

## Definition of Done (공통 체크)

- [ ] AC 충족  [ ] `./gradlew build` 통과(테스트 포함)  [ ] Flyway 적용 + Hibernate `validate` 통과
- [ ] 이중차감 불변식 테스트 통과(T3)  [ ] 소유권/열거차단 유지  [ ] 시크릿·키 미커밋/미로그
- [ ] Conventional Commits  [ ] 프론트 변경 시 `static/` 산출물 커밋  [ ] 결정사항 `docs/DECISIONS.md` 기록

## 권장 실행 순서

T1 → T2 → **T3(불변식 먼저 통과시키고 진행)** → T4 → T5. T6는 인터페이스만.
제품 검증을 빠르게 하려면 T4의 "광고손실 SKU" 집계 + T5 표를 **머니샷으로 먼저 얇게** 완성해 셀러에게 시연.

---

---

# 부록 — 루트 `CLAUDE.md` 에 추가할 블록

> 아래를 기존 `CLAUDE.md` 의 "패키지 구조" 목록과 "다음 단계"에 각각 붙여 넣으면,
> Claude Code 가 핸드오프 문서만 읽고도 이 기능 맥락을 잡는다.

**패키지 구조에 추가:**

```
- `ads/` — 광고비 인제스트(수기/CSV) + 광고 ROI 집계. 광고비를 SKU(vendor_item_id) 단위로
  귀속해 "광고전 기여이익 vs 광고비"로 광고손실 SKU를 적발. 소스는 v1 수기/CSV,
  쿠팡 광고 API 는 `ads/provider/AdSpendProvider` 뒤 후속. 상세: docs/ad-roi-spec.md
```

**"다음 단계 A"(사업자등록 전 가능) 에 추가:**

```
6. [신규 수익기능] 광고 ROI × 순이익 옵티마이저 — docs/ad-roi-spec.md / docs/ad-roi-tasks.md.
   CSV/수기 광고비 → SKU 귀속 → "광고 돌릴수록 손해인 SKU" 적발. 실 키 불필요(사업자 전 가능).
   ⚠️ 이중차감 주의: 광고비는 이제 ad_spends 로만 관리하고 ProfitCalculationService 기타비용
   배분에서 광고성 비용 제외(불변식: 전체 순이익 합계 불변). Flyway 다음 번호 V4.
```
